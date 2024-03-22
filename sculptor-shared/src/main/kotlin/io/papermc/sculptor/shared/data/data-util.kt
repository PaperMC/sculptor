package io.papermc.sculptor.shared.data

import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML

val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

val xml = XML {
    recommended()
    defaultPolicy {
        ignoreUnknownChildren()
    }
}
