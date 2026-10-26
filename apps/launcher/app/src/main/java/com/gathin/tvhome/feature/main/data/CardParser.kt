package com.gathin.tvhome.feature.main.data

import org.xmlpull.v1.XmlPullParser


object CardParser {

    @Throws(Exception::class)
    fun parse(p: XmlPullParser): Cards.Declared {
        var events = 0
        var found: Cards.Declared? = null
        var type = p.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            if (++events > Cards.MAX_EVENTS) throw Cards.CardException("more than ${Cards.MAX_EVENTS} XML events")
            if (type == XmlPullParser.START_TAG) {
                if (p.depth > Cards.MAX_DEPTH) throw Cards.CardException("XML nested deeper than ${Cards.MAX_DEPTH}")
                if (p.depth == 1) {
                    if (found != null) throw Cards.CardException("more than one root element")
                    val name = p.name
                    if ("card" != name) throw Cards.CardException("root element is <$name>, expected <card>")
                    found = readCard(p)
                }
            }
            type = p.next()
        }
        return found ?: throw Cards.CardException("no <card> element")
    }

    @Throws(Exception::class)
    private fun readCard(p: XmlPullParser): Cards.Declared {
        val count = p.attributeCount
        if (count > Cards.MAX_ATTRIBUTES) throw Cards.CardException("more than ${Cards.MAX_ATTRIBUTES} attributes on <card>")
        var spec: String? = null
        var label: String? = null
        var caption: String? = null
        var accent: String? = null
        for (i in 0 until count) {
            val ns = p.getAttributeNamespace(i)
            if (!ns.isNullOrEmpty()) continue
            val name = p.getAttributeName(i) ?: continue
            if (name.length > 8) continue
            when (name) {
                "spec" -> spec = p.getAttributeValue(i)
                "label" -> label = p.getAttributeValue(i)
                "caption" -> caption = p.getAttributeValue(i)
                "accent" -> accent = p.getAttributeValue(i)
            }
        }
        if (spec.isNullOrEmpty() || spec.length > 3 || !CardValidator.isDigits(spec)) {
            throw Cards.CardException("spec is missing or not a small integer")
        }
        val reason = CardValidator.checkLabel(label) ?: CardValidator.checkCaption(caption)
        if (reason != null) throw Cards.CardException(reason)
        var colour = 0
        if (!accent.isNullOrEmpty()) {
            if (CardValidator.isReference(accent)) throw Cards.CardException("accent is a resource reference; write #RRGGBB itself")
            colour = CardValidator.parseAccent(accent)
            if (colour == 0) throw Cards.CardException("accent is not #RRGGBB")
        }
        return Cards.Declared(spec.toInt(), label!!, if (caption.isNullOrEmpty()) null else caption, colour)
    }
}
