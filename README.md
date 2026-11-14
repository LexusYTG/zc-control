# Centro de control (estilo Pixel)

Panel de ajustes rápidos como overlay. Se arrastra hacia abajo desde el borde superior derecho y sigue al dedo: la primera bajada muestra notificaciones y 4 accesos; bajando de nuevo se abre la vista completa. El fondo se difumina con una captura reducida (accesibilidad `canTakeScreenshot`); si no está disponible usa el desenfoque del compositor (Android 12+).

## Compilar sin PC (desde Termux)
1. Creá un repo vacío en GitHub.
2. En Termux: `pkg install git`, entrá a esta carpeta y corré:
   `git init && git add . && git commit -m init && git branch -M main`
   `git remote add origin <URL-del-repo> && git push -u origin main`
3. En GitHub > Actions > "Build APK", esperá que termine y bajá el artefacto `centro-de-control-apk`.

## Compilar en PC
Abrí la carpeta en Android Studio y ejecutá Build > Build APK.

## Primer uso
Abrí la app con Shizuku corriendo: pide un solo permiso y configura accesibilidad, ajustes y No molestar solo.


## Novedades
- Vista acotada distinta por estilo (Pixel: píldoras 2x2; iOS: centro de notificaciones; Xiaomi: hora grande y ajustes; ColorOS: fila de 5 accesos).
- Zona de apertura configurable: izquierda, centro, derecha o todo el borde superior (por defecto, todo).
- Mantener apretado un acceso en la vista completa abre el editor: activar/quitar y ordenar. Se guarda por estilo.
- Accesos de apps (TileService): aparecen en el editor, desactivados por defecto. Se accionan con Shizuku (`cmd statusbar add-tile/click-tile`); no muestran estado.
- Horizontal: el contenido se limita a un ancho razonable y el panel se cierra al girar la pantalla.

## Pestañas y expansión
- Dos pestañas (Notificaciones / Controles) con deslizamiento lateral; en Controles están multimedia, brillo y volumen.
- La segunda bajada expande los accesos y empuja las notificaciones hacia abajo (no se superpone).
- Notificaciones desplegables (texto largo y acciones) y agrupadas por app.
- Barrita superior con opacidad ajustable (0 = invisible); la zona táctil sigue activa.
