
# ZC Control

### Una capa de personalización extremadamente modificable para Android, pensada para reemplazar por completo la del sistema

## ¿Qué es?

**ZC Control** es una capa de personalización para Android. Su objetivo es que **cada parte de la interfaz con la que interactúas a diario** (panel de ajustes rápidos, notificaciones, pantalla de inicio, accesos, ventanas, ajustes) **sea tuya y completamente modificable**: cómo se ve, cómo se comporta, qué muestra, dónde está y cómo se abre.

No pretende ser un tema ni un widget más. La idea es que, con el tiempo, la capa del fabricante deje de ser necesaria y toda la experiencia diaria pase por ZC Control, con un nivel de personalización mucho mayor que el que ofrece cualquier sistema de serie.

## ¿Qué hace hoy?

Estas son las piezas que **ya existen y funcionan**:

- **Panel de control propio.** Reemplaza al panel de ajustes rápidos y a la barra de notificaciones, y puede deshabilitar el original. Funciona en dos niveles: una primera bajada compacta (notificaciones y cuatro accesos) y una segunda con el centro de control completo. Casi todo es configurable: fondo y desenfoque, hoja, botones, colores, reloj, tipografía, tarjetas, brillo y volumen, reproductor, accesos propios, textos, diseño manual, zona de apertura, temas, animaciones y automatización.
- **Notificaciones.** El panel muestra y gestiona las notificaciones de todas las apps (para eso lee las notificaciones del sistema).
- **Launcher propio.** Pantalla de inicio con dock de 5 posiciones, botón Home central, cajones por categoría y navegación radial de dos etapas. Es opcional: solo actúa como pantalla de inicio si lo eliges como launcher.
- **Interfaz lateral.** Modo burbuja o modo gestos, con accesos configurables, notificaciones animadas y apertura de apps en ventanas flotantes cuando el sistema lo permite.
- **App de Ajustes propia.** Con buscador, desde donde se configura todo lo anterior. También ofrece un botón para abrir los ajustes del sistema.
- **Game Control.** Perfiles por juego. Al abrir un juego, ZC Control guarda el estado del sistema, cierra otras apps, ajusta resolución y DPI, cambia opciones de batería y actividades, puede forzar la compilación del juego y lo inicia; al salir, restaura todo. Mientras juegas puede mostrar CPU, RAM, GPU, FPS y temperatura.

## ¿Qué busca ser?

Hacia dónde va el proyecto, y lo que **todavía no está hecho**:

- Que **cada pantalla y cada elemento** de la capa reemplazada tenga sus propias opciones de apariencia y comportamiento, sin límites impuestos por el fabricante.
- Cubrir más superficies del sistema con la misma filosofía (hoy cubre panel, notificaciones, inicio, accesos laterales y ajustes propios).
- Que la configuración sea la **única fuente de verdad**: lo que se ve en pantalla sale de los ajustes, y agregar un ajuste es agregar una línea.
- Varios ajustes del árbol de configuración aún aparecen como «Pronto»: están planificados, no terminados.

## Límites (sé realista)

ZC Control **no es una ROM, no usa root y no modifica los archivos del sistema**. Android no permite que una app de terceros sustituya de verdad la interfaz del sistema, así que ZC Control lo consigue combinando cuatro cosas:

1. un **servicio de accesibilidad**, que dibuja sus propias ventanas por encima del sistema;
2. el **acceso a notificaciones**, para mostrarlas y descartarlas;
3. **ajustes seguros y comandos de shell** concedidos a través de **Shizuku**, por ejemplo para bloquear la expansión del panel original;
4. su propio **launcher**, para la pantalla de inicio.

Consecuencias: depende de Shizuku, de la versión de Android y del fabricante; algunas funciones pueden fallar o comportarse distinto según el dispositivo; y el sistema original sigue ahí debajo (por eso existe la función de restaurar).

## Permisos y privacidad

Para hacer lo anterior, ZC Control pide permisos **muy poderosos**: puede leer tus notificaciones, saber qué app está en primer plano, listar las apps instaladas y cambiar ajustes del sistema. Léelo antes de instalar.

- Al abrir la app por primera vez se muestra una **pantalla de consentimiento**. **Hasta que aceptes, ningún componente lee, pide ni modifica nada:** el servicio de accesibilidad y el de notificaciones quedan inertes, el receptor de arranque no hace nada y no se solicita ningún permiso ni acceso a Shizuku.
- Si rechazas, se te agradece, la app se cierra por completo y **borra sus propios datos**.
- El `AndroidManifest.xml` **no declara el permiso `INTERNET`**: la herramienta no solicita ni dispone de acceso a Internet y no transmite datos a terceros. Puedes comprobarlo tú mismo; la pantalla de consentimiento explica cómo.
- Todo se procesa de forma local en el dispositivo.

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
3. Abre ZC Control y lee la pantalla de consentimiento: si no aceptas, la aplicación se cierra y no hace nada.
4. Tras aceptar, autoriza el permiso de Shizuku que solicita la aplicación.
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
