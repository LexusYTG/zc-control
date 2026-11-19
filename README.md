<div align="center">

# 🦢 ZC Control

**Una capa de personalización extremadamente modificable para Android.**
Pensada para que cada parte de la interfaz con la que interactúas a diario sea tuya.

[![Release](https://img.shields.io/github/v/release/LexusYTG/zc-control?style=flat-square&color=2ea043)](https://github.com/LexusYTG/zc-control/releases)
[![Licencia](https://img.shields.io/badge/licencia-GPLv3-blue?style=flat-square)](LICENSE)
[![Plataforma](https://img.shields.io/badge/plataforma-Android-3ddc84?style=flat-square)](#requisitos)
[![Sin Internet](https://img.shields.io/badge/permiso%20INTERNET-no%20declarado-success?style=flat-square)](#-privacidad-y-consentimiento)
[![Ley 25.326](https://img.shields.io/badge/datos%20personales-Ley%2025.326-informational?style=flat-square)](#-privacidad-y-consentimiento)

[Qué es](#-qué-es) · [Módulos](#-módulos) · [Funciones](#-qué-hace-hoy) · [Privacidad](#-privacidad-y-consentimiento) · [Instalación](#-instalación-y-primer-uso) · [Recuperación](#%EF%B8%8F-advertencia-recuperación-del-panel-original) · [Compilación](#-compilación) · [Soporte](#-distribución-oficial-y-soporte) · [Licencia](#-licencia)

</div>

---

## ✨ Qué es

**ZC Control** es una capa de personalización para Android: panel de ajustes rápidos, notificaciones, pantalla de inicio, accesos, ventanas y ajustes. Todo pensado para que **cómo se ve, cómo se comporta, qué muestra, dónde está y cómo se abre** dependa de ti.

No pretende ser un tema ni un widget más. La idea es que, con el tiempo, la capa del fabricante deje de ser necesaria y la experiencia diaria pase por ZC Control, con un nivel de personalización mayor al de cualquier sistema de serie.

## 🧩 Módulos

Una sola app, cuatro módulos y un centro de permisos:

| Módulo | Qué hace |
|---|---|
| **ZC Control Center** | Panel de ajustes rápidos y notificaciones propio |
| **ZC Action Hub** | Burbuja flotante (o gesto de borde, experimental) |
| **ZC Game Center** | Modo juego con perfiles por juego |
| **ZC Launcher** | Pantalla de inicio propia |
| **ZC Permission Control** | Permisos, Shizuku, estado de los servicios y retiro del consentimiento |

La primera vez, después del consentimiento, un asistente pregunta el idioma (se lee de `res/raw/lengua.md`; agregar un idioma ahí lo suma solo) y si quieres el paquete completo o elegir módulo por módulo.

## 🚀 Qué hace hoy

Piezas que **ya existen y funcionan**:

- **Panel de control propio.** Reemplaza el panel de ajustes rápidos y la barra de notificaciones, y puede deshabilitar el original. Dos niveles: una primera bajada compacta (notificaciones y cuatro accesos) y una segunda con el centro de control completo. Casi todo es configurable: fondo y desenfoque, hoja, botones, colores, reloj, tipografía, tarjetas, brillo y volumen, reproductor, accesos propios, textos, diseño manual, zona de apertura, temas, animaciones y automatización.
- **Notificaciones.** El panel muestra y gestiona las notificaciones de todas las apps.
- **Launcher propio.** Dock de 5 posiciones, botón Home central, cajones por categoría y navegación radial de dos etapas. Es opcional: solo actúa como pantalla de inicio si lo eliges como launcher.
- **Interfaz lateral.** Modo burbuja o modo gestos, con accesos configurables, notificaciones animadas y apertura de apps en ventanas flotantes cuando el sistema lo permite.
- **App de Ajustes propia.** Con buscador, desde donde se configura todo lo anterior, y un botón para abrir los ajustes del sistema.
- **Game Control.** Perfiles por juego. Al abrir un juego guarda el estado del sistema, cierra otras apps, ajusta resolución y DPI, cambia opciones de batería y actividades, puede forzar la compilación del juego y lo inicia; al salir, restaura todo. Mientras juegas puede mostrar CPU, RAM, GPU, FPS y temperatura.

### 🔭 Hacia dónde va

- Que **cada pantalla y cada elemento** tenga sus propias opciones de apariencia y comportamiento.
- Cubrir más superficies del sistema con la misma filosofía.
- Que la configuración sea la **única fuente de verdad**: lo que se ve sale de los ajustes, y agregar un ajuste es agregar una línea.
- Varios ajustes aparecen como «Pronto»: están planificados, no terminados.

## 🧱 Límites (sé realista)

ZC Control **no es una ROM, no usa root y no modifica los archivos del sistema**. Android no permite que una app de terceros sustituya de verdad la interfaz del sistema, así que se apoya en cuatro cosas:

1. Un **servicio de accesibilidad**, que dibuja sus propias ventanas por encima del sistema.
2. El **acceso a notificaciones**, para mostrarlas y descartarlas.
3. **Ajustes seguros y comandos de shell** concedidos por **Shizuku**, por ejemplo para bloquear la expansión del panel original.
4. Su propio **launcher**, para la pantalla de inicio.

Consecuencias: depende de Shizuku, de la versión de Android y del fabricante; algunas funciones pueden fallar o comportarse distinto según el dispositivo; y el sistema original sigue ahí debajo (por eso existe la restauración).

## 🔐 Privacidad y consentimiento

ZC Control pide permisos **muy poderosos**: puede leer tus notificaciones, saber qué app está en primer plano, listar las apps instaladas y cambiar ajustes del sistema. **Léelo antes de instalar.**

Al abrir la app por primera vez se muestra un **EULA formal con política de tratamiento de datos personales**, redactado conforme a la **Ley 25.326** de la República Argentina. Incluye la identificación del desarrollador, la finalidad, los derechos de acceso, rectificación y supresión, y cómo verificar todo por tu cuenta. El consentimiento se da con **dos casillas separadas**: una por el contrato y otra por el tratamiento local de datos.

| Garantía | Detalle |
|---|---|
| **Nada sin tu permiso** | Hasta que aceptes, ningún componente lee, pide ni modifica nada: los servicios quedan inertes, el receptor de arranque no hace nada y no se solicita ningún permiso ni acceso a Shizuku. |
| **Si rechazas** | La app se cierra por completo y **borra sus propios datos**. |
| **Sin Internet** | El `AndroidManifest.xml` **no declara el permiso `INTERNET`** y el código no usa conexiones de red. No transmite datos a terceros. |
| **Todo local** | Todo se procesa en el dispositivo; el desarrollador no recibe ni mantiene ninguna base de datos con información tuya. |
| **Retiro del consentimiento** | El botón **«Retirar consentimiento y borrar todo»**, en el centro de permisos, quita los permisos, restaura el panel y la barra del sistema, borra los datos de la app y la cierra. |
| **Nuevas versiones del EULA** | Si el texto cambia de forma que te afecte, la app vuelve a pedir tu aceptación. |

La app guarda en tu dispositivo la versión del EULA y la fecha y hora de tu aceptación. Si activaste las copias de seguridad de Android, el sistema podría incluir los datos propios de la app en ellas; eso lo controlas tú desde los ajustes del dispositivo.

**Cómo comprobarlo tú mismo:**

- Abre el `AndroidManifest.xml` de la app y verifica que **no** exista el permiso `INTERNET`.
- Entra en «Información de la aplicación → Uso de datos» y comprueba que no haya tráfico de red.
- Si encuentras `INTERNET` o cualquier comportamiento no descrito aquí, desinstala, no vuelvas a ejecutarla y revisa tu dispositivo.

> Los derechos sobre datos personales (acceso, rectificación, supresión) se pueden ejercer al instante desde la propia app o, si lo prefieres, por el correo indicado en el EULA. Es la única excepción a la regla de no atender solicitudes fuera de GitHub Issues.

## 📦 Requisitos

- Un dispositivo Android compatible.
- Shizuku instalado y en funcionamiento.
- Los servicios y permisos necesarios para que la app pueda realizar las modificaciones del sistema.

Algunas funciones dependen de las capacidades y restricciones de cada versión de Android y del fabricante.

## 🛠 Instalación y primer uso

1. Descarga e instala ZC Control (desde las [Releases](https://github.com/LexusYTG/zc-control/releases) del repositorio oficial o compilándolo tú).
2. Inicia Shizuku y comprueba que funcione correctamente.
3. Abre ZC Control y lee el EULA: si no aceptas, la app se cierra y no hace nada.
4. Tras aceptar, autoriza el permiso de Shizuku que solicita la app.
5. La app configura automáticamente los servicios de accesibilidad, los ajustes correspondientes y No molestar.

Una vez completada la configuración, ya puedes usar el centro de control alternativo.

## ⚠️ Advertencia: recuperación del panel original

> **Lee esta sección antes de utilizar la aplicación.**

ZC Control puede deshabilitar el panel de control original de Android para que el personalizado ocupe su lugar. Si lo deshabilitas y luego **desinstalas ZC Control sin restaurarlo**, podrías quedarte sin acceso al panel del sistema. En ese caso la recuperación podría requerir ADB y, en el peor de los casos (extremo e improbable), restablecer el dispositivo de fábrica.

> **Regla fundamental: nunca desinstales ZC Control sin restaurar antes el panel de control original.**

**Cómo evitarlo:** antes de desinstalar, restaura el panel original, o usa **«Retirar consentimiento y borrar todo»**, que lo restaura por ti (con Shizuku activo).

<details>
<summary><b>Ya desinstalé la app y perdí el panel</b></summary>

1. Vuelve a instalar ZC Control.
2. Abre la aplicación y acepta el EULA.
3. Busca el botón **«Restaurar / Restablecer sistema»** y ejecuta la restauración del panel original.
4. Comprueba que el panel de Android vuelva a funcionar.
5. Ahora sí puedes desinstalar ZC Control sin inconvenientes.

Con ADB, también puedes restaurarlo manualmente:

```bash
adb shell cmd statusbar send-disable-flag none
```

</details>

## 🔨 Compilación

ZC Control se compila desde Linux o desde Android con Termux, con Gradle y ADB disponibles. Usa el Gradle instalado en el entorno, sin Gradle Wrapper.

```bash
git clone https://github.com/LexusYTG/zc-control.git
cd zc-control
gradle assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. El tiempo depende del dispositivo y de si las dependencias ya están descargadas.

**Sobre las compilaciones `debug`:** el proyecto usa compilaciones de depuración para que cualquiera pueda obtenerlo y compilarlo sin infraestructura propia de firma.

## 🏛 Distribución oficial y soporte

### Único origen oficial

**El único lugar oficial de ZC Control es este repositorio:**

👉 **https://github.com/LexusYTG/zc-control**

Todo lo que no provenga de aquí **no es ZC Control oficial**, aunque tenga el mismo nombre o ícono. Las [Releases](https://github.com/LexusYTG/zc-control/releases) del repositorio incluyen un APK `debug`; como cada persona firma con su propia clave, **no existe una firma oficial para verificar un APK ya hecho**. La forma más segura de obtener la app es **compilarla tú desde el repositorio oficial**.

### No está en ninguna tienda

ZC Control **no está publicada** ni pretende estarlo en tiendas ni sitios de descargas: ni Google Play, APKPure, APKMirror, Aptoide, Uptodown, APKCombo, APKVision, ni las tiendas de fabricantes (Samsung, Huawei, Amazon, Xiaomi, OPPO, vivo, realme, Honor, etc.), ni canales de Telegram, WhatsApp o Discord, ni enlaces de Mega, MediaFire, Drive o Dropbox, ni videos con enlaces de descarga.

La única tienda a la que aspira es **[F-Droid](https://f-droid.org/)**, por ser de código abierto y compilar desde el código fuente. **Hoy todavía no está.** Si algún día se publica, se anunciará en este README, y solo contará como oficial el paquete que F-Droid compile y distribuya desde este código.

<details>
<summary><b>Si lo instalaste desde cualquier otro sitio</b></summary>

- **No la avalamos.** No sabemos qué contiene ese archivo: puede estar modificado, desactualizado, con publicidad, rastreadores o código malicioso.
- **No damos soporte** ni atendemos reportes o reclamos sobre copias de origen distinto al repositorio oficial.
- **No respondemos por esa copia.** Las declaraciones de privacidad y consentimiento describen únicamente el código de este repositorio; no podemos cumplirlas ni garantizarlas sobre un paquete que no distribuimos nosotros.
- **No estamos de acuerdo** con que se distribuya como si fuera una versión oficial.

La GPLv3 permite redistribuir y modificar el código, pero exige identificar las versiones modificadas como tales (ver «Términos adicionales»). Que algo sea redistribuible no lo convierte en oficial.

</details>

<details>
<summary><b>Qué soporte se ofrece y qué no</b></summary>

ZC Control es un proyecto voluntario. **No hay garantías de plazo, de respuesta ni de corrección**, y se ofrece «tal cual».

**Se atienden** los reportes que cumplan todo esto:

1. Compilada desde el **código del repositorio oficial**, sin modificaciones.
2. Versión más reciente del repositorio, o una cercana.
3. Requisitos cumplidos (Shizuku instalado, iniciado y con permiso concedido).
4. Reporte en **[GitHub Issues](https://github.com/LexusYTG/zc-control/issues)** con: modelo del dispositivo, versión de Android, descripción, pasos para reproducirlo y, si puedes, capturas o registros.
5. Un problema por reporte.

**No se atienden:**

- Copias de cualquier origen distinto al repositorio oficial.
- Versiones modificadas o recompiladas por terceros.
- Compilaciones antiguas cuando el problema ya está corregido.
- Instalaciones sin Shizuku o con los permisos sin conceder.
- Restricciones del fabricante o de Android (ahorro de batería agresivo, bloqueo de servicios en segundo plano, capas del fabricante). Se pueden reportar como incompatibilidad, sin garantía de solución.
- Daños o pérdidas de datos por usar la herramienta sin haber leído las advertencias.
- Reportes, reseñas o mensajes en tiendas, foros, redes o sitios de terceros: **no se revisan**.
- Solicitudes de soporte por mensajes privados, correo u otro canal fuera del repositorio (la excepción son los derechos sobre datos personales).

</details>

<details>
<summary><b>Fallos, daños y reclamos</b></summary>

- Si algo falla en una copia compilada desde el repositorio oficial, repórtalo en GitHub Issues. Se revisará durante el desarrollo, sin plazo garantizado.
- Si falla una copia de otro origen, **el reclamo corresponde a quien te la entregó o publicó**.
- Si perdiste el panel original o el sistema quedó raro, sigue **primero** la sección de recuperación.
- El autor **no asume responsabilidad por daños** derivados del uso, la modificación o la redistribución del software (GPLv3, secciones 15 y 16, y términos adicionales), sin perjuicio de los derechos irrenunciables que correspondan por ley.
- Si sospechas que una copia es maliciosa, desinstálala y denuncia el sitio que la distribuye. No la envíes a este proyecto como si fuera un error de ZC Control.

</details>

## 🐞 Reporte de errores

ZC Control está en pleno desarrollo. Si encuentras errores, incompatibilidades o problemas, repórtalos en [GitHub Issues](https://github.com/LexusYTG/zc-control/issues) con el modelo del dispositivo, la versión de Android, la descripción, los pasos para reproducirlo y, si puedes, capturas o registros. Los reportes ayudan a mejorar la compatibilidad y se revisan durante el desarrollo.

## 🌱 Estado del proyecto

Desarrollo en evolución: algunas funciones pueden cambiar o corregirse a medida que avance. Se agradecen los reportes, las pruebas y la colaboración.

## 📄 Licencia

Copyright © 2025-2026 LexusYTG

Este programa es software libre: puedes redistribuirlo y/o modificarlo bajo los términos de la Licencia Pública General de GNU, versión 3 o (a tu elección) cualquier versión posterior. Se distribuye con la esperanza de que sea útil, pero **SIN NINGUNA GARANTÍA**, ni siquiera la implícita de comerciabilidad o idoneidad para un propósito particular. Consulta <https://www.gnu.org/licenses/>. El texto completo está en [LICENSE](LICENSE).

<details>
<summary><b>Términos adicionales (Sección 7 de la GPLv3)</b></summary>

1. **Exención de garantía y limitación de responsabilidad:** además de las secciones 15 y 16 de la GPLv3, el autor no ofrece ninguna garantía y no asume responsabilidad por daños derivados del uso, modificación o redistribución de este software.
2. **Preservación de avisos legales:** cualquier redistribuidor debe preservar los avisos de copyright y las atribuciones de autoría de los archivos originales.
3. **Prohibición de tergiversar el origen:** las versiones modificadas deben estar claramente identificadas como distintas del software original ZC Control (antes «Centro de control») y no deben presentarse como la versión original.
4. **Limitación de uso del nombre con fines publicitarios:** los nombres «LexusYTG» y «ZC Control (antes «Centro de control»)» no pueden usarse para respaldar o promocionar versiones modificadas sin permiso previo por escrito.
5. **Renuncia a derechos de marca:** no se concede ningún derecho de marca sobre «ZC Control (antes «Centro de control»)» ni sobre los logotipos asociados.
6. **Indemnización:** quien distribuya el software (o versiones modificadas) asumiendo responsabilidades contractuales frente al receptor debe indemnizar a los licenciantes y autores por cualquier responsabilidad derivada de dichas asunciones.

</details>

---

<div align="center">

**🦢 ZC Control — Una experiencia de control diferente para Android.**

Copyright © 2025-2026 LexusYTG

</div>
