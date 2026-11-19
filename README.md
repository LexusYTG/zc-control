
# ZC Control

### Una capa de personalización extremadamente modificable para Android, pensada para reemplazar por completo la del sistema

## ¿Qué es?

**ZC Control** es una capa de personalización para Android. Su objetivo es que **cada parte de la interfaz con la que interactúas a diario** (panel de ajustes rápidos, notificaciones, pantalla de inicio, accesos, ventanas, ajustes) **sea tuya y completamente modificable**: cómo se ve, cómo se comporta, qué muestra, dónde está y cómo se abre.

No pretende ser un tema ni un widget más. La idea es que, con el tiempo, la capa del fabricante deje de ser necesaria y toda la experiencia diaria pase por ZC Control, con un nivel de personalización mucho mayor que el que ofrece cualquier sistema de serie.

## Estructura de la app (una sola app, cuatro módulos)

Al abrir la app se ve la pantalla principal con cuatro módulos y, abajo, permisos y ajustes del sistema:

- **ZC Control Center**: panel de ajustes rápidos y notificaciones propio.
- **ZC Action Hub**: burbuja flotante (o gesto de borde, experimental).
- **ZC Game Center**: modo juego con perfiles.
- **ZC Launcher**: pantalla de inicio propia.
- **ZC Permission Control**: permisos, Shizuku y estado de los servicios.

La primera vez, después del consentimiento, un asistente pregunta el idioma (se lee de `res/raw/lengua.md`, agregar un idioma ahí lo suma solo) y si se quiere el paquete completo o elegir módulo por módulo.

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

## Distribución oficial, soporte y reclamos

### Único origen oficial

**El único lugar oficial de ZC Control es este repositorio:**

👉 **https://www.github.com/LexusYTG/zc-control/**

Todo lo que no provenga de ahí **no es ZC Control oficial**, aunque tenga el mismo nombre, el mismo ícono o diga serlo.

### ZC Control no está en ninguna tienda

Esta aplicación **no está publicada** y **no pretende estarlo** en ninguna tienda de aplicaciones ni sitio de descargas. En particular, **no está** en:

- Google Play Store
- APKVision
- APKPure
- APKMirror
- Aptoide
- Uptodown
- APKCombo
- Samsung Galaxy Store
- Huawei AppGallery
- Amazon Appstore
- Xiaomi GetApps / Mi Store
- Tiendas de otros fabricantes (OPPO, vivo, realme, Honor, etc.)
- Canales de Telegram, grupos de WhatsApp, servidores de Discord
- Enlaces de Mega, MediaFire, Google Drive, Dropbox o similares
- Videos de YouTube con enlaces de descarga
- Cualquier otra tienda, repositorio, foro o sitio no mencionado aquí

**La única tienda en la que aspira a estar es [F-Droid](https://f-droid.org/)**, por ser de código abierto y por compilar las aplicaciones desde su código fuente. **Hoy todavía no está en F-Droid.** Si algún día se publica, se anunciará en este mismo README, en el repositorio oficial, y solo contará como oficial el paquete que F-Droid compile y distribuya desde este código.

### Si lo instalaste desde cualquier otro sitio

Si obtuviste esta herramienta de cualquier sitio o repositorio que **no sea el oficial**:

- **No la avalamos.** No sabemos qué contiene ese archivo: puede estar modificado, desactualizado, empaquetado con publicidad, con rastreadores o con código malicioso.
- **No damos soporte.** No atendemos reportes, dudas ni reclamos sobre copias de origen distinto al repositorio oficial.
- **No respondemos por esa copia.** Las declaraciones de privacidad y consentimiento de este proyecto describen únicamente el código de este repositorio. No podemos cumplirlas ni garantizarlas sobre un paquete que no distribuimos nosotros, porque físicamente no tenemos forma de saber qué se instaló en tu dispositivo.
- **No estamos de acuerdo** con que se distribuya como si fuera una versión oficial.

La licencia (GPLv3) permite que otras personas redistribuyan y modifiquen el código, pero exige que las versiones modificadas se identifiquen como tales (ver «Términos adicionales» al final de este documento). Que algo sea legalmente redistribuible no lo convierte en oficial ni nos hace responsables de ello.

### Cómo comprobar lo que instalaste

Las compilaciones de este proyecto son de tipo `debug` y cada persona que compila las firma con su propia clave, así que **no existe una firma oficial con la que verificar un APK ya hecho**. La forma más segura de obtener ZC Control es **compilarlo tú mismo desde el repositorio oficial** (ver la sección «Compilación»).

Además, puedes comprobar cualquier copia:

- Abre su `AndroidManifest.xml` y verifica que **no** exista el permiso `INTERNET`.
- Revisa en «Información de la aplicación → Uso de datos» que no haya tráfico de red.
- Si encuentras `INTERNET` o cualquier comportamiento que no esté descrito en este README, desinstala, no vuelvas a ejecutarla y revisa tu dispositivo.

### Qué soporte se ofrece y qué no

ZC Control es un proyecto mantenido de forma voluntaria. **No hay garantías de plazo, de respuesta ni de corrección**, y la herramienta se ofrece «tal cual», según la licencia.

**Se atienden** reportes que cumplan todo lo siguiente:

1. La app fue compilada desde el **código del repositorio oficial**, sin modificaciones.
2. Es la versión más reciente del repositorio, o una cercana a ella.
3. Cumples los requisitos de este README (Shizuku instalado, iniciado y con permiso concedido).
4. El reporte se hace en **[GitHub Issues](https://github.com/LexusYTG/zc-control/issues)** e incluye: modelo del dispositivo, versión de Android, descripción del problema, pasos para reproducirlo y, si puedes, capturas o registros.
5. Un problema por reporte.

**No se atienden:**

- Copias obtenidas de cualquier origen distinto al repositorio oficial.
- Versiones modificadas, con partes cambiadas o recompiladas por terceros.
- Compilaciones antiguas cuando el problema ya está corregido en la versión actual.
- Instalaciones sin Shizuku, o con los permisos del sistema sin conceder.
- Comportamientos causados por restricciones del fabricante o de la versión de Android (ahorro de batería agresivo, bloqueo de servicios en segundo plano, capas del fabricante, etc.). Se pueden reportar como incompatibilidad, pero no está garantizado que tengan solución.
- Daños o pérdidas de datos derivados de usar la herramienta sin haber leído las advertencias de este documento.
- Reportes, reseñas, comentarios o mensajes publicados en tiendas, foros, redes o sitios de terceros: **no se revisan**.
- Solicitudes de soporte por mensajes privados, correo o cualquier otro canal fuera del repositorio.

### Fallos, daños y reclamos

- Si algo falla en una copia compilada desde el repositorio oficial, repórtalo en GitHub Issues como se indica arriba. Se revisará durante el desarrollo, sin plazo garantizado.
- Si algo falla en una copia de otro origen, **el reclamo corresponde a quien te la entregó o la publicó**, no a este proyecto.
- Si perdiste el panel original o el sistema quedó en un estado raro, sigue **primero** el procedimiento de la sección «ADVERTENCIA IMPORTANTE: recuperación del panel original». Es la vía de recuperación prevista.
- El autor **no asume responsabilidad por daños** derivados del uso, la modificación o la redistribución del software (GPLv3, secciones 15 y 16, y términos adicionales).
- Si sospechas que una copia es maliciosa, desinstálala y denuncia el sitio que la distribuye. No la envíes a este proyecto como si fuera un error de ZC Control.

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
