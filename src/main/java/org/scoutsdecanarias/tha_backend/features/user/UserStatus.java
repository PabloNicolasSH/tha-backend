package org.scoutsdecanarias.tha_backend.features.user;

public enum UserStatus {
    /** Ha solicitado la cuenta; la federación aún no la ha aprobado. No tiene contraseña. */
    PENDING,
    /** Aprobada: se le envió un enlace para crear su contraseña y aún no lo ha usado. */
    INVITED,
    ACTIVE,
    DISABLED
}
