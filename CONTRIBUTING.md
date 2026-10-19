# Cómo contribuir a tha-backend

Este repositorio sigue **git flow**. Todo el trabajo entra por **pull request** y con el **CI en verde**.

## Ramas

| Rama | Para qué |
|---|---|
| `main` | Lo que está (o estará) en producción. Solo recibe `release/*` y `hotfix/*`. Cada versión lleva una etiqueta `vX.Y.Z`. |
| `develop` | Integración. Es la **rama por defecto**: las PR van aquí. |
| `feature/F0-12-nombre-corto` | Una historia de usuario. Sale de `develop` y vuelve a `develop`. El número es el de la historia del backlog. |
| `release/0.1.0` | Preparar una versión: sale de `develop` y se fusiona en `main` y en `develop`. |
| `hotfix/descripcion` | Arreglo urgente en producción: sale de `main` y se fusiona en `main` y en `develop`. |
| `bugfix/descripcion` | Corrección que no es una historia, desde `develop`. |

```bash
git checkout develop && git pull
git checkout -b feature/F0-12-administracion-usuarios
```

## Commits

Se escriben **siempre en inglés** y siguen [*Conventional Commits*](https://www.conventionalcommits.org/):

```
feat: add the scout group management screen
fix: reject expired invitation links
test: cover the role matrix for group coordinators
docs: explain how to run the integration tests
```

- Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- Asunto en imperativo, en minúscula y sin punto final.
- El cuerpo explica **qué y por qué**, no cómo.
- **No** se añaden firmas de coautoría (`Co-Authored-By`) ni similares.

## Pull requests

1. Abre la PR contra `develop` con la plantilla. El **título** sigue Conventional Commits (lo valida un check).
2. Describe cómo probarlo y adjunta capturas si cambia la interfaz.
3. El CI (`Build and test`) y la validación del título deben estar en verde.
4. Se fusiona con **merge commit** (no *squash*), para conservar la historia de la rama, y se borra la rama.

## Antes de abrir la PR

```bash
./mvnw verify          # compila y ejecuta todos los tests (necesita Docker)
```

- Una migración nueva de Flyway lleva el número siguiente (`V5__descripcion.sql`) y **nunca se edita una ya aplicada**.
- Si tocas permisos, añade el test de la **matriz de roles** (propio, ajeno, sin permiso).
- Sin secretos ni datos personales en el código, los logs ni los tests.
- Textos de interfaz en español claro.

## Protección de ramas (la aplica el propietario)

Estos pasos son configuración de GitHub, no código, así que los ejecuta quien administra el repositorio **una sola vez**:

```bash
# 1. Publicar main (hoy solo existe develop en el remoto) y hacer de develop la rama por defecto
git push origin main
gh repo edit PabloNicolasSH/tha-backend --default-branch develop

# 2. Proteger main y develop: exigir PR y los checks, sin force push ni borrado
for BRANCH in main develop; do
  gh api -X PUT repos/PabloNicolasSH/tha-backend/branches/$BRANCH/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["Build and test", "Validate PR title"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 0, "dismiss_stale_reviews": true },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
done

# 3. Solo merge commits
gh repo edit PabloNicolasSH/tha-backend --enable-merge-commit --enable-squash-merge=false --enable-rebase-merge=false --delete-branch-on-merge
```

`enforce_admins` queda en `false` para que el administrador pueda saltarse la regla en una emergencia. Con más personas en el equipo, sube `required_approving_review_count` a 1.
