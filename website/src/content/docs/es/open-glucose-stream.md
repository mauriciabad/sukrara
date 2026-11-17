---
title: Open Glucose Stream
description: "Comparte tu glucosa sin nube: el estándar abierto detrás de la función de compartir de Sukrara. Cifrada en tu móvil, transportada por relays públicos o Bluetooth, legible solo por las personas que has aprobado."
---

:::caution[Trabajo en curso]
Esta especificación todavía se está desarrollando y puede cambiar en cualquier momento. Cuando
salga Sukrara 1.0, sus versiones se consolidarán: solo se conservará la última, los borradores anteriores se
eliminarán y la numeración de versiones volverá a empezar. No confíes en «borrador 3» como nombre o número estable.
:::

Sukrara comparte tu glucosa con las personas que eliges. Estamos convirtiendo la forma en que lo hace en un **estándar
abierto**, el Open Glucose Stream (OGS), para que cualquier app pueda sumarse: un reloj, un widget, Home Assistant, el
visor de una clínica u otra app de MCG que tu familia ya use.

- **Privado por diseño.** Las lecturas se cifran en tu móvil y solo las personas que has aprobado pueden leerlas. Los
  relays solo las transmiten. Nada público dice que un flujo es tuyo: las claves que ven los relays cambian cada día, y
  en los datos no hay ningún nombre, número de serie ni cuenta.
- **Sin cuenta, sin servidor.** Lo transportan relays Nostr públicos cuando estáis lejos, y el Bluetooth cuando estáis
  cerca, incluso sin internet.
- **Tú decides qué y durante cuánto tiempo.** Una invitación dice lo que ofrece: si pueden añadir insulina e hidratos,
  cuánto historial ven y durante cuántos días. Un canguro para el fin de semana, una excursión del colegio, la clínica
  antes de tu cita: el acceso termina solo, y lo garantiza el cifrado, no una promesa. Quitar a alguien cambia las
  claves, así que no recibe nada nuevo y ya ni siquiera puede reconocer tu flujo.
- **Pensado para cuidadores.** Ambos padres pueden gestionar el flujo de un niño. Los cuidadores pueden confirmar una
  alerta, para que los demás sepan que alguien se está ocupando. Una tarjeta de emergencia opcional deja que un técnico
  de emergencias vea tu glucosa durante un día, y siempre se te avisa.
- **Abierto.** La especificación, los vectores de prueba y una implementación de referencia independiente son públicos,
  bajo CC0 (sin condiciones, apps cerradas incluidas):
  [la especificación OGS](https://github.com/mauriciabad/open-glucose-stream).

**En qué punto estamos.** El borrador actual es el **borrador 3**, una propuesta que aún no implementa ninguna app.
Sukrara usa hoy el borrador 1, anterior, y pasar al borrador 3 implica volver a invitar a tus seguidores. Lo diremos
claramente antes de que pase. El [informe de contexto](/research/24b-open-standard-draft3/) explica cada decisión de
diseño. Lo que Sukrara implementa hoy, regla por regla, está en
[su especificación del protocolo](https://github.com/mauriciabad/sukrara/blob/main/contracts/protocol/PROTOCOL-v2.md),
junto a los [vectores de prueba y secuencias de eventos](https://github.com/mauriciabad/sukrara/tree/main/contracts/protocol)
con los que se comprueban las dos apps de Sukrara y el seguidor de referencia independiente.

**Para desarrolladores.** La
[lista de comprobación de conformidad](https://github.com/mauriciabad/open-glucose-stream/blob/main/spec/conformance.md)
dice lo que debe hacer una app lectora, colaboradora, publicadora o administradora. Los
[vectores de prueba](https://github.com/mauriciabad/open-glucose-stream/tree/main/test-vectors) te permiten comprobar
tu app byte a byte sin tocar nunca datos de salud reales. La sección de puentes lo traduce todo a Nightscout, HL7 FHIR,
Health Connect, HealthKit y Tidepool.

*No es un producto sanitario. La copia de un seguidor puede llegar con retraso. No tomes decisiones de tratamiento
basándote en ella.*
