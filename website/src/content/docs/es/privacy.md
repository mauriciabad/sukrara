---
title: Política de privacidad
description: Qué guarda Sukrara en tu móvil, qué envía y adónde (solo cuando activas una función) y cómo borrarlo. Sin cuenta, sin servidor, sin analíticas.
---

*Sukrara para Android (la versión de Google Play y la de GitHub), Sukrara para Mac (la versión de la App Store y la de
GitHub) y esta web. Última modificación: 2026-11-15; cada cambio está en [el historial del proyecto](https://github.com/mauriciabad/sukrara/commits/main/website/src/content/docs/es/privacy.md).*

**En resumen:** Sukrara no tiene cuentas, ni servidor propio, ni analíticas, ni anuncios, ni informes de fallos. Tu
glucosa, tus tratamientos y tus ajustes se quedan en tu móvil. Algo sale del móvil solo cuando activas una función que
lo envía, y solo adonde indica esa función: las personas con las que compartes (con cifrado de extremo a extremo, así
que nadie por el camino puede leerlo) o un servicio en el que inicias sesión tú (LibreView, Tidepool, tu sitio de
Nightscout). El desarrollador nunca recibe nada de ello.

Sukrara lo desarrolla Maurici Abad (España) como software libre
([código fuente](https://github.com/mauriciabad/sukrara), GPL-3.0-or-later). Preguntas o solicitudes:
**support-googleplay@a.mauri.app**, o [una incidencia en GitHub](https://github.com/mauriciabad/sukrara/issues).

## Qué se queda en tu móvil

- **Lecturas de glucosa** de tu sensor (o de la persona a la que sigues): se guardan 90 días.
- **Tratamientos** que añades (unidades de insulina rápida o lenta, hidratos en gramos, una nota corta) y dosis leídas
  de una pluma de insulina (con el número de serie de la pluma): se guardan hasta que los borres o desinstales Sukrara.
- **Tus ajustes**, tu nombre de perfil y la imagen generada (que solo ven las personas con las que compartes), y el
  emparejamiento del sensor.
- **Claves e inicios de sesión**: las claves para compartir, los tokens de LibreView, Tidepool y Nightscout cuando los
  usas, y tu correo y contraseña de LibreView en cuanto inicias sesión en LibreView (así inicias sesión una sola vez
  para iniciar sensores y para la subida a LibreView; *Cerrar sesión* los borra). Se cifran con una clave del Android
  Keystore del móvil. El correo de una cuenta de LibreView o Tidepool se guarda en el almacenamiento propio de Sukrara
  para que los ajustes puedan mostrar qué cuenta tiene la sesión iniciada.
- **Un registro de diagnóstico** que nunca sale del móvil salvo que lo copies tú (*Copiar informe*).

Solo Sukrara puede leer su almacenamiento. La copia de seguridad de Android y la transferencia entre dispositivos están
desactivadas para Sukrara, así que nada de ello va a una copia de seguridad de Google. Desinstalar Sukrara, o *Borrar
almacenamiento* en los ajustes de Android para Sukrara, lo borra todo.

## Qué sale de tu móvil, y solo cuando lo activas

| Función (dónde la activas) | Qué se envía | Destino |
|---|---|---|
| **Compartir con personas por internet** (paso *Tus seguidores* de la configuración o Ajustes › Compartir; ahí se puede desactivar *Por internet*) | Lecturas, alertas, tratamientos y notas, los ajustes que replican los seguidores, tu nombre y tu imagen: todo con cifrado de extremo a extremo (NIP-44) para las personas a las que has dado acceso. Una copia de seguridad de tu configuración de compartir (tus seguidores con sus nombres e imágenes, tus invitaciones abiertas, las claves) va cifrada de extremo a extremo con tu propia clave, así que solo tu móvil (o aquel en el que la restaures) puede leerla. Tu móvil los publica desde el momento en que se configura la función de compartir, para que un seguidor pueda ponerse al día. | Relays Nostr públicos (por defecto nos.lol, relay.primal.net, nostr.mom, relay.snort.social), por conexiones cifradas. Los relays no pueden leer el contenido. Sí ven tu dirección IP, la versión de Sukrara, claves públicas aleatorias (una de ellas no cambia mientras compartas), la hora, el tamaño y el tipo de cada mensaje (una lectura en directo, una alerta, datos guardados, un mensaje de clave o de invitación) y cuándo caduca, y cuándo alguien sigue el mismo flujo. |
| **Compartir por Bluetooth** (Ajustes › Compartir › *Por Bluetooth cerca*, activado en cuanto tienes un seguidor; *Emparejar cerca*) | Los mismos mensajes cifrados que reciben los relays, de móvil a móvil: cualquier dispositivo al alcance del Bluetooth que los pida los recibe, y solo tus seguidores pueden leerlos. Mientras *Emparejar cerca* está abierto (hasta 10 minutos), los dispositivos al alcance también pueden leer tu nombre, tu imagen, un código de invitación de un solo uso y tus relays; los dos confirmáis un código de 6 cifras antes de que se comparta nada. | Dispositivos cercanos (los móviles y Macs de tus seguidores lo leen) |
| **Enlace o código QR de invitación** (Ajustes › Compartir) | Un enlace de un solo uso, válido 24 horas. Su secreto va después del `#`, que los navegadores nunca envían a esta web. Quien tenga el enlace puede ver tu nombre, tu imagen y tus relays y pedir seguirte; tú decides con *Permitir*. | A quien se lo des |
| **Inicio de sesión en LibreView** (la configuración, un sensor nuevo, Ajustes › Sensor o Compartir con apps › LibreView: una sola cuenta para la app) | Tu correo y contraseña de LibreView, para obtener el ID de receptor de tu cuenta (para iniciar sensores Libre 3). Se guardan cifrados en el móvil hasta que cierres sesión, así que inicias sesión una sola vez. | Abbott (api.libreview.io) |
| **Subida a LibreView** (Compartir con apps › LibreView) | Tu correo y contraseña de LibreView cuando inicia sesión (con la cuenta anterior); después, lecturas de glucosa con su hora (y, por tanto, tu zona horaria) y tendencia, insulina, hidratos y notas, inicios de sensor, un ID de dispositivo aleatorio para esta instalación, el número del sensor actual (que se marca como sensor activo de la cuenta), y el nombre y la fecha de nacimiento que ya tiene tu cuenta de LibreView. | Abbott (LibreView; sus ajustes, de fsll3.freestyleserver.com) |
| **Subida a Tidepool** (Compartir con apps › Tidepool) | Tu correo y contraseña de Tidepool al iniciar sesión; después, lecturas de glucosa con su hora y tendencia, insulina, hidratos, notas, tu zona horaria, el modelo del sensor, la versión de Sukrara y un nombre de dispositivo aleatorio. Un seguidor puede activarla para la persona a la que sigue: entonces las lecturas de esa persona van a la cuenta de Tidepool con la que inicia sesión el seguidor. *Importar historial* desde Tidepool inicia sesión del mismo modo y solo descarga. | Tidepool (api.tidepool.org) |
| **LibreLinkUp como origen** (configuración inicial o Ajustes › Origen, en el móvil) | El correo y la contraseña de LibreLinkUp con los que inicias sesión (guardados cifrados en el móvil con el token que da LibreLinkUp, hasta que cierres sesión; nunca en una copia ni una exportación); Sukrara lee la glucosa, su tendencia y el número y el inicio del sensor de la persona a la que sigue la cuenta. No envía nada más: mientras LibreLinkUp es tu origen, la subida a LibreView no envía nada. | Abbott (api.libreview.io y sus servidores regionales, api-&lt;región&gt;.libreview.io) |
| **Nightscout como origen** (configuración inicial o Ajustes › Origen, en el móvil o el Mac) | Tu token de acceso (que se cambia por un inicio de sesión de corta duración) o tu API secret (enviado solo como su hash SHA-1), guardado cifrado en el dispositivo; Sukrara lee la glucosa y los tratamientos del sitio y, con *Tratamientos en ambos sentidos*, escribe la insulina, los hidratos y las notas que registras aquí con un nombre de dispositivo aleatorio. Solo a una dirección `https://`. Nada de lo que vino del sitio se vuelve a subir a él. | Tu propio sitio de Nightscout (o el de la persona) |
| **Subida a Nightscout** (Compartir con apps › Nightscout) | Lecturas de glucosa, insulina, hidratos, notas, cambios de sensor (con el número del sensor), el nivel de batería del móvil y si se está cargando, y un nombre de dispositivo aleatorio. Solo a una dirección `https://`. | Tu propio sitio de Nightscout |
| **Health Connect** (Compartir con apps › Health Connect) | Tus lecturas de glucosa (solo escritura; Sukrara no lee nada de Health Connect). Si lo desactivas, se quedan en Health Connect; bórralas allí. | Health Connect, en tu móvil |
| **Apps de este móvil** (Compartir con apps: avisos a apps, servidor xDrip+, API de Nightscout) | Cada lectura nueva (y el número del sensor) a las apps que elijas, una a una. Mientras está activado, el servidor xDrip+ deja que cualquier app de este móvil lea las últimas 24 horas de glucosa; la API de Nightscout (lecturas y tratamientos) necesita su secreto, y *También en este Wi-Fi* la sirve sin cifrar a los dispositivos de tu red de casa. Con Juggluco como fuente, Sukrara lo lee en el propio móvil. | Apps de este móvil; dispositivos de tu Wi-Fi si lo permites |
| **Fin del sensor en tu calendario** (Ajustes › Fin del sensor) | Un evento cuando termina el sensor, con su número. Por defecto, en el calendario propio de Sukrara en el móvil; si eliges el calendario de una de tus cuentas, esa cuenta lo sincroniza. | Tu calendario |
| **Copia de seguridad** (Ajustes › Copia de seguridad) | Tu clave para compartir, guardada como contraseña en el gestor de contraseñas que elijas, o mostrada como un código de recuperación protegido con tu propia contraseña. | Tu gestor de contraseñas |
| **Exportar datos** (Ajustes › Exportar datos) | Un archivo CSV de lecturas y tratamientos, guardado o compartido donde elijas. | Donde elijas |
| **Informar de un problema**, *Enviar comentarios* | Abre GitHub en tu navegador; *Enviar comentarios* sobre un sensor no probado rellena el modelo de tu móvil, las versiones de Android y de Sukrara y el tipo de sensor. No se envía nada salvo que lo envíes allí. | GitHub, si lo envías |

Todo esto está desactivado hasta que lo actives, salvo la propia función de compartir: en cuanto la configuras se usan
los relays, y también el Bluetooth en cuanto alguien te sigue, porque así es como funciona compartir (Ajustes ›
Compartir › *Por internet* y *Por Bluetooth cerca* los desactivan). Todas las conexiones que hace Sukrara van cifradas
(`https` o `wss`), salvo las opcionales en tu propio móvil o en tu red de casa mencionadas arriba.

Sukrara nunca pide tu ubicación, tus contactos, el micrófono ni tus archivos. La cámara solo se usa para escanear un
código QR de invitación, en el móvil. El Bluetooth encuentra el sensor y los móviles cercanos, nunca tu ubicación.

**Solo en la versión de GitHub** (no en la de Google Play): Sukrara pide a GitHub la lista de versiones de Sukrara una
vez al día (Ajustes › Acerca de › Actualizaciones, activado por defecto) y al abrir Acerca de, y descarga una
actualización de GitHub cuando la instalas; GitHub ve tu dirección IP, no se envía nada más.

## Las personas que te siguen

Reciben lo que compartes: lecturas, alertas, inicio y fin del sensor, tratamientos con notas, los ajustes que
replican, tu nombre y tu imagen. Su móvil guarda las lecturas 90 días (un Mac, 31 días). Si les permites añadir
entradas, pueden enviarte tratamientos y notas. Quitar a alguien detiene lo que recibe a partir de ese momento; lo que
ya ha recibido se queda en su dispositivo.

## Sukrara para Mac

La app de Mac sigue a quien lleva el sensor: se conecta a los mismos relays y al móvil de quien lleva el sensor por
Bluetooth, y envía tu solicitud para seguir (con un nombre, por defecto el del ordenador, y una imagen), los mensajes
cifrados que mantienen el seguimiento en marcha (peticiones de claves nuevas y de lecturas perdidas) y, si se permite,
las entradas que añades, todo con cifrado de extremo a extremo.
*Juggluco en esta red*, cuando lo activas, lee la glucosa de Juggluco en tu red de casa: sin una dirección, lo busca en
las direcciones privadas de la red de casa, y envía un hash del secreto de la API de Juggluco si has puesto uno (http
sin cifrar, solo en la red de casa). La versión de GitHub (el dmg o Homebrew) lee la lista de actualizaciones de esta
web (`sukrara.mauri.app/appcast.xml`) una vez al día y cuando eliges *Buscar actualizaciones…*, y descarga una
actualización de GitHub; la versión de la App Store no hace ninguna de las dos cosas (la actualiza la App Store).
Guarda las lecturas 31 días y tus claves en el Llavero del Mac, solo en ese Mac; nada va a iCloud. La cámara lee un
código QR de invitación solo mientras el escáner está abierto: no se graba ni se envía nada.

## Cuánto dura cada cosa y cómo borrarla

- **En tu móvil o Mac:** lecturas 90 días (Mac, 31), tratamientos hasta que los borres. Si borras un tratamiento,
  también se borra para tus seguidores y en LibreView, Tidepool y Nightscout. Desinstala la app para borrarlo todo,
  claves incluidas. *Dejar de seguir*, en el dispositivo de un seguidor, borra de ese dispositivo lo que ha recibido de
  ti.
- **En los relays:** cada mensaje lleva una caducidad, tras la cual los relays lo borran: las lecturas en directo y las
  alertas, tras 1 hora; el historial de lecturas, tras 7 días; los tratamientos, los ajustes y la lista de seguidores,
  tras 30 días; la copia de seguridad cifrada de tu configuración de compartir, tras 90 días. Sukrara no envía
  solicitudes de borrado aparte; los relays que gestionan otros deciden cómo respetan la caducidad.
- **LibreView, Tidepool, Nightscout:** lo que se subió se queda en tu cuenta de ese servicio después de cerrar sesión;
  bórralo allí.
- **Gestor de contraseñas, calendario, Health Connect, archivos exportados:** son tuyos; bórralos allí.

El desarrollador no guarda ninguno de tus datos, así que por nuestra parte no hay nada que borrar. Escribe a la
dirección de arriba si tienes alguna pregunta.

## Menores

Sukrara no recoge nada de nadie, menores incluidos. El padre o la madre que configura Sukrara en el móvil de un menor
decide cuáles de las funciones anteriores están activadas.

## Esta web

sukrara.mauri.app es un sitio estático en GitHub Pages, sin analíticas ni cookies; GitHub lo sirve y ve tu dirección
IP. Todo lo que muestra, fuentes y búsqueda incluidas, viene del propio sitio: no se contacta con ningún otro servicio.
La búsqueda funciona en tu navegador, la página de invitación lee la invitación solo en tu navegador, y tu navegador
recuerda el tema que eliges y si has cerrado el aviso de idioma (en su almacenamiento local, nunca se envía). Los
botones de descarga obtienen los archivos de GitHub.

## No es un producto sanitario

Sukrara no es un producto sanitario y no diagnostica, trata, cura ni previene ninguna enfermedad o afección. FreeStyle
Libre y LibreView son marcas comerciales de Abbott; Sukrara no tiene ninguna vinculación con Abbott ni cuenta con su
respaldo.
