package ApunteIA.BackEnd.exception

/** Error interno de correo sin destinatario, token ni credenciales en su mensaje. */
class EmailDeliveryException : RuntimeException("No se pudo entregar el correo de recuperación")
