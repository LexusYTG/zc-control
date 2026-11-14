# Centro de control (estilo Pixel)

Panel de ajustes rápidos como overlay. Se arrastra hacia abajo desde el borde superior derecho y sigue al dedo: la primera bajada muestra notificaciones y 4 accesos; bajando de nuevo se abre la vista completa. El fondo se difumina con una captura reducida (accesibilidad `canTakeScreenshot`); si no está disponible usa el desenfoque del compositor (Android 12+).

## Compilar sin PC (desde Termux)
1. Creá un repo vacío en GitHub.
2. En Termux: `pkg install git`, entrá a esta carpeta y corré:
   `git init && git add . && git commit -m init && git branch -M main`
   `git remote add origin https://github.com/LexusYTG/CentroDeControl.git && git push -u origin main`
3. En GitHub > Actions > "Build APK", esperá que termine y bajá el artefacto `centro-de-control-apk`.

## Compilar en PC
Abrí la carpeta en Android Studio y ejecutá Build > Build APK.

## Primer uso
Abrí la app con Shizuku corriendo: pide un solo permiso y configura accesibilidad, ajustes y No molestar solo.

## Licencia

Copyright (C) 2025 LexusYTG

Este programa es software libre: puedes redistribuirlo y/o modificarlo bajo los términos de la Licencia Pública General de GNU, publicada por la Free Software Foundation, ya sea la versión 3 de la Licencia, o (a tu elección) cualquier versión posterior.

Este programa se distribuye con la esperanza de que sea útil, pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR. Consulta la Licencia Pública General de GNU para más detalles.

Deberías haber recibido una copia de la Licencia Pública General de GNU junto con este programa. Si no, consulta <https://www.gnu.org/licenses/>.

El texto completo de la licencia está en el archivo [LICENSE](LICENSE).

### Términos adicionales (Sección 7, GPLv3)

De conformidad con la Sección 7 de la GPLv3, se aplican los siguientes términos adicionales:

1. **Exención de garantía y limitación de responsabilidad**: además de lo dispuesto en las secciones 15 y 16 de la GPLv3, el autor no ofrece ninguna garantía y no asume ninguna responsabilidad por daños derivados del uso, modificación o redistribución de este software.

2. **Preservación de avisos legales**: cualquier redistribuidor debe preservar los avisos de copyright y las atribuciones de autoría presentes en los archivos originales.

3. **Prohibición de tergiversar el origen**: las versiones modificadas deben estar claramente identificadas como distintas del software original Centro de control, y no deben presentarse como la versión original.

4. **Limitación de uso del nombre con fines publicitarios**: los nombres "LexusYTG" y "Centro de control" no pueden usarse para respaldar o promocionar versiones modificadas sin permiso previo por escrito.

5. **Renuncia a derechos de marca**: no se concede ningún derecho de marca sobre "Centro de control" ni sobre los logotipos asociados.

6. **Indemnización**: quien distribuya el software (o versiones modificadas) asumiendo responsabilidades contractuales frente al receptor debe indemnizar a los licenciantes y autores por cualquier responsabilidad derivada de dichas asunciones.
