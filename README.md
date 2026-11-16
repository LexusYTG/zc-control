
# ZC Control

### Un centro de control completamente personalizable para Android

**ZC Control** es una herramienta de personalización avanzada para Android diseñada para reemplazar la experiencia del panel de control tradicional por una interfaz completamente configurable.

A diferencia de una aplicación convencional que simplemente dibuja una superposición (*overlay*) sobre la pantalla, ZC Control busca ofrecer un nivel mucho más profundo de integración con el sistema operativo, permitiendo modificar la experiencia de uso del centro de control y deshabilitar el panel original de Android.

El objetivo es proporcionar un entorno de control alternativo, flexible y altamente personalizable, con una experiencia de interacción inspirada en los paneles de ajustes rápidos de los dispositivos Google Pixel.

---

## Características principales

### 1. Centro de control personalizado

ZC Control incorpora un panel de control alternativo que permite sustituir la experiencia del panel de ajustes rápidos original.

Su funcionamiento está organizado en dos niveles:

- **Primera apertura:** al deslizar hacia abajo desde el borde superior derecho, se muestra una interfaz compacta con las notificaciones y cuatro accesos rápidos.
- **Segunda apertura:** al continuar deslizando hacia abajo, se despliega la vista completa del centro de control.

La transición entre ambos niveles está diseñada para mantener una interacción natural, fluida y sincronizada con el movimiento del dedo.

### 2. Integración profunda con Android

ZC Control no se limita a representar visualmente un panel alternativo.

La herramienta interviene en la configuración del sistema Android para conseguir que la experiencia personalizada sustituya al panel de control original.

Entre sus funciones de integración se encuentran:

- Deshabilitación del panel de control original.
- Configuración de los servicios de accesibilidad necesarios.
- Modificación de los ajustes del sistema compatibles.
- Configuración de No molestar.
- Integración con los mecanismos de administración del sistema mediante Shizuku.

Estas operaciones se realizan mediante la integración con Shizuku, evitando que el usuario tenga que configurar manualmente cada ajuste.

### 3. Fondo dinámico y efecto de desenfoque

Para conseguir una apariencia visual más integrada con Android, el panel utiliza un fondo difuminado.

El mecanismo contempla dos alternativas:

- **Captura de pantalla:** utiliza la capacidad de accesibilidad `canTakeScreenshot` para obtener una captura reducida de la pantalla y emplearla como fondo.
- **Desenfoque del compositor:** cuando la captura no está disponible, recurre al desenfoque proporcionado por el compositor del sistema en Android 12 o versiones posteriores.

De esta manera, se contempla una alternativa para mantener el efecto visual incluso cuando el mecanismo principal no está disponible.

### 4. Interfaz lateral (burbuja o gestos)

ZC Control incluye un subsistema opcional de acceso rápido que se configura en **Make your action bar → INTERFAZ LATERAL** (también hay un botón en la pantalla principal). Tiene dos modos que comparten exactamente los mismos accesos, ventanas y notificaciones; solo cambia cómo se abre el panel:

- **Modo burbuja:** una burbuja pequeña anclada al borde derecho. Al tocarla se abre un panel con accesos directos configurables (agregar, quitar, reordenar). Si te estorba, **mantenela apretada 10 segundos** (configurable de 3 a 15): se abre un *modo lienzo* y la arrastrás adonde quieras. «Borde derecho» la devuelve a su sitio.
- **Modo gestos:** sin ningún elemento visible. Deslizá desde el borde derecho hacia la izquierda y el panel sale siguiendo tu dedo. **Solo se habilita con la navegación de tres botones** de Android, para no chocar con los gestos del sistema.
- **Notificaciones dinámicas:** al llegar una notificación, la burbuja se desprende, sube por una trayectoria vertical hasta arriba, se despliega en una tarjeta (app + contenido), se queda unos 5 segundos (configurable) y vuelve a su lugar. Tocá la tarjeta para abrir la app o deslizala hacia arriba para descartarla. En modo gestos aparece una burbuja temporal que sale del borde derecho y se vuelve a esconder.
- **Ventanas flotantes:** las apps se abren en modo ventana cuando el sistema y la app lo permiten (con Shizuku se usa el modo freeform por shell; hay un botón para preparar los ajustes necesarios). Si no, se abren normalmente.

Requiere el servicio de accesibilidad de ZC Control y, para las notificaciones, el acceso a notificaciones que ya se concede junto con el resto de permisos.

---

## Requisitos

Antes de utilizar ZC Control, es necesario disponer de:

- Un dispositivo Android compatible.
- Shizuku instalado y en funcionamiento.
- Los servicios y permisos necesarios para que la aplicación pueda realizar las modificaciones del sistema.

**Importante:** algunas funciones dependen de las capacidades y restricciones de cada versión de Android y del fabricante del dispositivo.

---

## Instalación y primer uso

1. Descarga e instala ZC Control.
2. Inicia Shizuku y comprueba que se encuentre funcionando correctamente.
3. Abre ZC Control.
4. Autoriza el permiso solicitado por la aplicación.
5. La herramienta realizará automáticamente las configuraciones necesarias para establecer su funcionamiento.

Durante este proceso se configuran los servicios de accesibilidad, los ajustes correspondientes y No molestar.

Una vez completada la configuración, podrás utilizar el centro de control alternativo.

---

## ADVERTENCIA IMPORTANTE: recuperación del panel original

> **Lee esta sección antes de utilizar la aplicación.**

ZC Control tiene la capacidad de deshabilitar el panel de control original de Android para permitir que la experiencia personalizada ocupe su lugar.

Esto implica una consideración importante:

Si deshabilitas el panel original y posteriormente desinstalas ZC Control sin restaurarlo previamente, podrías quedarte sin acceso al panel de control del sistema.

En ese escenario, la recuperación podría requerir intervención mediante ADB.

En el peor de los casos, y como situación extrema e improbable, podría llegar a ser necesario restablecer el dispositivo de fábrica.

### ¿Cómo evitar este problema?

Antes de desinstalar ZC Control, debes restaurar el panel original.

**Procedimiento de recuperación:**

1. Si ya desinstalaste la aplicación y perdiste el panel original, vuelve a instalar ZC Control.
2. Abre la aplicación.
3. Busca el botón **«Restaurar / Restablecer sistema»**.
4. Ejecuta la restauración del panel original.
5. Comprueba que el panel de control de Android vuelva a funcionar.
6. Una vez restaurado, podrás desinstalar ZC Control sin inconvenientes.

> **Regla fundamental: nunca desinstales ZC Control sin restaurar previamente el panel de control original.**

La función de restauración existe precisamente para permitir revertir los cambios realizados por la herramienta.

---

## Compilación

ZC Control puede compilarse directamente desde cualquier entorno Linux o Android mediante Termux, siempre que se disponga de las herramientas necesarias para ejecutar Gradle y ADB.

El proyecto utiliza Gradle instalado en el entorno, sin necesidad de utilizar Gradle Wrapper.

### Compilar desde Termux o Linux

**1. Clonar el repositorio**

```bash
git clone https://github.com/LexusYTG/zc-control.git
```

**2. Entrar al directorio del proyecto**

```bash
cd zc-control
```

**3. Compilar el APK**

```bash
gradle assembleDebug
```

Espera a que Gradle finalice el proceso de compilación.

El tiempo necesario dependerá del dispositivo, del entorno de ejecución y de si las dependencias ya se encuentran descargadas.

**4. Instalar la aplicación**

Con el dispositivo conectado y ADB disponible, ejecuta:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

¡Listo! ZC Control estará instalado en el dispositivo.

### Ubicación del APK generado

Una vez finalizada correctamente la compilación, el APK de depuración se encuentra en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Sobre las compilaciones de depuración

ZC Control utiliza compilaciones de tipo `debug`.

Esto permite que los usuarios puedan obtener y compilar el proyecto sin necesidad de disponer de una infraestructura propia de firma para versiones de producción.

---

## Reporte de errores y problemas

ZC Control es un proyecto que se encuentra en pleno desarrollo.

Si encuentras errores, comportamientos inesperados, incompatibilidades o problemas durante la instalación y utilización, te invitamos a reportarlos.

Los informes de errores ayudan a identificar problemas, mejorar la compatibilidad y perfeccionar el funcionamiento de la herramienta.

Al reportar un problema, procura incluir:

- Modelo del dispositivo.
- Versión de Android.
- Descripción del problema.
- Pasos necesarios para reproducirlo.
- Capturas de pantalla o registros, cuando estén disponibles.

**Los errores reportados serán revisados y corregidos durante el desarrollo del proyecto.**

### Reportar un problema

Puedes utilizar el sistema de seguimiento de errores de GitHub:

[ZC Control - Issues](https://github.com/LexusYTG/zc-control/issues)

---

## Estado del proyecto

ZC Control es un desarrollo en evolución.

La herramienta continúa en proceso de desarrollo, por lo que algunas funciones pueden experimentar cambios, mejoras o correcciones a medida que avance el proyecto.

Se agradecen los reportes, las pruebas y la colaboración de quienes deseen contribuir a mejorar su funcionamiento.

---

## Licencia

Copyright (C) 2025-2026 LexusYTG

Este programa es software libre: puedes redistribuirlo y/o modificarlo bajo los términos de la Licencia Pública General de GNU, publicada por la Free Software Foundation, ya sea la versión 3 de la Licencia, o (a tu elección) cualquier versión posterior.

Este programa se distribuye con la esperanza de que sea útil, pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR. Consulta la Licencia Pública General de GNU para más detalles.

Deberías haber recibido una copia de la Licencia Pública General de GNU junto con este programa. Si no, consulta <https://www.gnu.org/licenses/>.

El texto completo de la licencia está en el archivo [LICENSE](LICENSE).

### Términos adicionales (Sección 7 de la GPLv3)

De conformidad con la Sección 7 de la GPLv3, se aplican los siguientes términos adicionales:

1. **Exención de garantía y limitación de responsabilidad:** además de lo dispuesto en las secciones 15 y 16 de la GPLv3, el autor no ofrece ninguna garantía y no asume ninguna responsabilidad por daños derivados del uso, modificación o redistribución de este software.

2. **Preservación de avisos legales:** cualquier redistribuidor debe preservar los avisos de copyright y las atribuciones de autoría presentes en los archivos originales.

3. **Prohibición de tergiversar el origen:** las versiones modificadas deben estar claramente identificadas como distintas del software original ZC Control (antes «Centro de control»), y no deben presentarse como la versión original.

4. **Limitación de uso del nombre con fines publicitarios:** los nombres «LexusYTG» y «ZC Control (antes «Centro de control»)» no pueden usarse para respaldar o promocionar versiones modificadas sin permiso previo por escrito.

5. **Renuncia a derechos de marca:** no se concede ningún derecho de marca sobre «ZC Control (antes «Centro de control»)» ni sobre los logotipos asociados.

6. **Indemnización:** quien distribuya el software (o versiones modificadas) asumiendo responsabilidades contractuales frente al receptor debe indemnizar a los licenciantes y autores por cualquier responsabilidad derivada de dichas asunciones.

---

**ZC Control — Una experiencia de control diferente para Android.**

Copyright © 2025-2026 LexusYTG.
