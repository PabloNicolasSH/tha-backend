# tha-backend

[![CI](https://github.com/PabloNicolasSH/tha-backend/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/PabloNicolasSH/tha-backend/actions/workflows/ci.yml)

API de **Tha** (Scouts de Canarias). Spring Boot 4.1 · Java 21 · MySQL 8.4 · Flyway.

## Arrancar en local

```bash
docker compose up -d          # MySQL (puerto 3307) y Mailpit (http://localhost:8025, bandeja de correos de prueba)
./mvnw spring-boot:run        # perfil "dev" activo, puerto 8080
```

Cuenta de desarrollo (solo si la BD está vacía): `admin@tha.local` / `admin12345`.
Usa el puerto 8080 (el de Spring Boot por defecto). Si tienes otro servicio en el 8080, como ECATLIM, no pueden ir a la vez: para cambiar el de Tha añade `--server.port=XXXX` y ajusta `proxy.conf.json` del frontend.

> Si Maven falla con un error de certificado (PKIX), usa el almacén de Windows:
> `MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"`.

## Tests

El **CI** de GitHub Actions (`.github/workflows/ci.yml`) compila y ejecuta estos mismos tests en cada pull request hacia `develop` o `main`, y en cada `push` a esas ramas. Si falla un test, el informe de Surefire queda como artefacto de la ejecución.

```bash
./mvnw test                   # requiere Docker (Testcontainers levanta un MySQL real, uno solo para toda la ejecución)
```

## Cómo se entra en Tha

### 1. Alta de cuentas: sin contraseña, con aprobación

1. La persona **solicita** la cuenta (`POST /api/auth/register`: nombre, apellidos, email). Queda `PENDING`. No hay contraseña.
2. La federación (rol `ADMIN` o `SECRETARY`) **aprueba** (`POST /api/admin/account-requests/{id}/approve`). La cuenta pasa a `INVITED` y se envía un email con un **enlace para crear la contraseña**.
3. La persona abre el enlace y crea su contraseña (`POST /api/auth/set-password`). La cuenta pasa a `ACTIVE`.

El enlace lleva un token de **un solo uso** que vive **en memoria (Caffeine)** durante `tha.invite.expiration` (48 h). Solo se guarda su hash SHA-256 y cada persona tiene como mucho uno válido: reenviar la invitación invalida el anterior. **Si el servidor se reinicia los enlaces pendientes se pierden**: se reenvían desde la bandeja de solicitudes.

| Endpoint | Descripción |
|---|---|
| `POST /api/auth/register` | Solicitud de cuenta. 202. |
| `GET /api/admin/account-requests` | Solicitudes abiertas (`PENDING` e `INVITED`). ADMIN/SECRETARY. |
| `POST /api/admin/account-requests/{id}/approve` | Aprueba y envía el enlace. |
| `POST /api/admin/account-requests/{id}/resend-invitation` | Enlace nuevo (el anterior deja de valer). |
| `DELETE /api/admin/account-requests/{id}` | Rechaza y elimina la solicitud. |
| `GET /api/auth/set-password/check?token=` | 204 si el enlace sigue valiendo, 410 si no. No lo gasta. |
| `POST /api/auth/set-password` | Crea la contraseña con el token. 204 / 410. |

### 2. Sesión: access token + refresh token

| Pieza | Detalle |
|---|---|
| Access token | JWT HS256 de **15 min**. Cabecera `Authorization: Bearer …`. El frontend lo guarda **solo en memoria**. Lleva `roles` y `typ=access`. |
| Refresh token | Valor opaco de 256 bits. En BD solo su hash SHA-256 (`refresh_token`). Cookie `tha_refresh`: `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, `Secure` en producción. |
| Rotación | Cada `POST /api/auth/refresh` entrega un refresh token nuevo e invalida el usado. |
| Reutilización | Si llega un token ya usado o revocado se asume robo: se **revoca toda la familia** de ese login. |
| **Mantener sesión iniciada** | `rememberMe: true` en el login → cookie **persistente de 30 días** (`tha.refresh.expiration`). Sin marcarlo → **cookie de sesión** (se borra al cerrar el navegador) y el token caduca a las 12 h (`tha.refresh.session-expiration`). La opción se conserva al rotar. |

| Endpoint | Descripción |
|---|---|
| `POST /api/auth/login` | Sesión (cuerpo + cookie) **o** desafío 2FA (ver abajo). |
| `POST /api/auth/refresh` | Canjea la cookie por un access token y un refresh token nuevos. |
| `POST /api/auth/logout` | Revoca la sesión y borra la cookie. |
| `GET /api/auth/me` | Usuario actual. |

### 3. Verificación en dos pasos (2FA)

Cada persona elige uno o ambos métodos en *Seguridad de la cuenta*:

- **App autenticadora (TOTP, RFC 6238)**: Google/Microsoft Authenticator, Aegis, 1Password… Se activa escaneando un QR (generado en el servidor, el secreto no sale a ningún servicio externo) y confirmando con un código.
- **Código por correo**: 6 dígitos, válido 10 min (`tha.mfa.email-code-ttl`), un solo uso, 5 intentos y 30 s entre envíos. Vive en memoria (Caffeine), solo su hash.
- **Códigos de recuperación**: 10 de un solo uso, solo su hash. Se entregan al activar el primer método y al regenerarlos; no vuelven a mostrarse.

Flujo de login con 2FA: la contraseña correcta **no abre sesión**; devuelve `{mfaRequired, mfaToken, methods, preferredMethod}` sin cookie. El `mfaToken` es un JWT de 5 min con `typ=mfa` que **solo sirve para `/api/auth/mfa/verify`**: el decodificador de la API exige `typ=access`, así que no vale como access token. Con el código correcto se abre la sesión (y se respeta "mantener sesión").

Protecciones: el secreto TOTP se guarda **cifrado con AES-256-GCM** (`tha.mfa.encryption-key`); un código TOTP ya usado no se acepta de nuevo; **5 fallos seguidos bloquean el segundo factor 15 min** aunque se tenga la contraseña; desactivar un método o regenerar los códigos exige la contraseña.

| Endpoint | Descripción |
|---|---|
| `POST /api/auth/mfa/verify` | Segundo paso del login: `{mfaToken, type: TOTP\|EMAIL\|RECOVERY, code}`. |
| `POST /api/auth/mfa/email/send` | Reenvía el código por correo durante el login. |
| `GET /api/auth/mfa` | Estado del 2FA del usuario. |
| `POST /api/auth/mfa/totp/setup` · `/totp/confirm` | Alta de la app autenticadora (QR + secreto, luego confirmar con un código). |
| `POST /api/auth/mfa/email/enable` · `/email/confirm` | Alta del código por correo. |
| `POST /api/auth/mfa/{TOTP\|EMAIL}/disable` | Desactiva un método (pide contraseña). |
| `POST /api/auth/mfa/recovery-codes` | Códigos de recuperación nuevos (pide contraseña). |
| `PUT /api/auth/mfa/preferred` | Método que se ofrece primero. |

> Spring Security 7 incluye MFA (`@EnableMultiFactorAuthentication`, `FactorGrantedAuthority`) pero está pensado para aplicaciones con **sesión** y no trae TOTP; esta API no tiene sesión, así que el 2FA se monta sobre las piezas de Spring Security (JWT, validadores de token, cifrado) con `java-otp` para el TOTP y `zxing` para el QR.

### 4. SSO (OpenID Connect)

Con **Spring Security OAuth2 Client**. Está **desactivado por defecto**; se activa con el perfil `sso` y un proveedor OIDC (Google, Microsoft Entra ID, Keycloak, Okta…). Ver `application-sso.properties` para las variables (`THA_SSO_ISSUER`, `THA_SSO_CLIENT_ID`, `THA_SSO_CLIENT_SECRET`, `THA_SSO_NAME`) y la URL de redirección que hay que registrar en el proveedor: `<URL pública>/login/oauth2/code/sso`.

```bash
SPRING_PROFILES_ACTIVE=dev,sso THA_SSO_ISSUER=… THA_SSO_CLIENT_ID=… THA_SSO_CLIENT_SECRET=… ./mvnw spring-boot:run
```

- **El SSO no crea cuentas**: solo identifica. Debe existir ya una cuenta `ACTIVE` aprobada por la federación. La primera vez se enlaza por **email verificado por el proveedor** (`email_verified`) y queda guardada la identidad (proveedor + `sub`) en `external_identity`; después entra por esa identidad.
- Solo se aceptan proveedores OIDC. Sin email verificado no se enlaza nunca por email.
- Tras el baile OAuth la sesión HTTP se descarta, se abre la sesión de Tha (cookie de refresh de sesión) y se redirige al frontend (`/sso/completar`). La API sigue sin estado.
- El proveedor ya pide sus propios factores, así que no se pide el 2FA de Tha en el SSO.
- Sin proveedor configurado, `/oauth2/**` está cerrado y `GET /api/auth/sso/providers` devuelve `[]` (el frontend no muestra el botón).

### Probar el SSO en local (sin cuenta en Google ni Microsoft)

`compose.yaml` incluye un **Keycloak de pruebas** (servicio opcional, no se levanta con el `docker compose up` normal; la imagen pesa unos 500 MB) con el realm `tha` ya configurado (`keycloak/tha-realm.json`).

```bash
docker compose --profile sso up -d keycloak        # http://localhost:8180  (admin / admin)
```

Arranca el backend con los perfiles **`dev,sso-local`** (en IntelliJ: la configuración **ThaBackend (dev + SSO local)**). Keycloak tiene que estar levantado **antes**, porque el backend lee su configuración al iniciar. En la pantalla de login aparece el botón **"Entrar con Cuenta corporativa (local)"**.

| Usuario de Keycloak | Contraseña | Resultado |
|---|---|---|
| `lucia.e2e@example.org` | `sso-dev-clave-2026` | Tiene cuenta activa en Tha: entra (y queda enlazada la identidad) |
| `sin.cuenta@example.org` | `sso-dev-clave-2026` | No tiene cuenta en Tha: vuelve al login con el aviso "sin cuenta" y no se crea nada |

Para repetir una prueba como otra persona hay que cerrar también la sesión en Keycloak (`http://localhost:8180/realms/tha/protocol/openid-connect/logout`).

> Comprobado de punta a punta con este Keycloak: botón → proveedor (con PKCE) → vuelta a Tha → sesión abierta, y el caso sin cuenta. Con un proveedor real (Google, Microsoft…) el procedimiento es el mismo; solo cambian las variables `THA_SSO_*` y la URL de redirección registrada.
> Un detalle de Spring: con `issuer-uri`, si el proveedor está caído al arrancar el backend, **no arranca**. Es el comportamiento por defecto y conviene tenerlo presente en producción.

> Lo que está probado: la lógica de Tha tras el proveedor (qué cuenta se abre y a dónde se redirige), con tests. El baile real con un proveedor concreto solo se puede comprobar contra ese proveedor.
> Si en vez de consumir SSO se quiere que **Tha sea el proveedor** (por ejemplo para ECATLIM), la pieza es *Spring Authorization Server*; no está incluida.

## Seguridad de dependencias

El POM sobrescribe `tomcat.version` y `jackson-bom.version` con parches de seguridad por encima de lo que gestiona Spring Boot 4.1.1 (3 vulnerabilidades críticas de Tomcat y 7 altas/moderadas de Jackson 3.1.5). Se puede volver a comprobar con OSV: `./mvnw dependency:list` y consultar `api.osv.dev`. Quitar esas dos líneas cuando Spring Boot incorpore versiones iguales o superiores.

## Variables de entorno (producción)

| Variable | Para qué |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | MySQL |
| `JWT_SECRET` | Firma de los JWT (≥ 32 caracteres) |
| `MFA_ENCRYPTION_KEY` | Clave AES-256 (32 bytes en Base64) que cifra los secretos TOTP. **Estable y secreta**: si cambia, hay que volver a configurar las apps autenticadoras. |
| `THA_FRONTEND_URL` | URL del frontend (CORS y enlaces de los correos) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | Correo |
| `THA_SSO_*` | Solo con el perfil `sso` |

Generar una clave de cifrado: `node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"`.

Frontend y API deben ser del mismo sitio (por ejemplo `tha.example.org` y `api.tha.example.org`, o la misma URL con un proxy) para que la cookie `SameSite=Strict` viaje. Detrás de un proxy inverso con HTTPS, `server.forward-headers-strategy=framework` ya está activado.

## Pendiente

- Límite de intentos del **login por contraseña** (el 2FA ya tiene el suyo).
- Los tokens de invitación y los códigos por correo en memoria no sobreviven a reinicios ni se comparten entre varias instancias del backend; con más de una instancia habría que moverlos a la BD o a Redis.

## Estructura

```
core/       seguridad, correo, excepciones, utilidades transversales
features/   auth, user, mfa, sso, scout_group, … (una carpeta por funcionalidad)
shared/     piezas reutilizables
```
