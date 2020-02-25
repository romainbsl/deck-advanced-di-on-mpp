package ws.slides

import kotlinx.css.*
import kotlinx.css.properties.TextDecoration
import react.child
import react.functionalComponent
import styled.*
import ws.kpres.PresentationBuilder
import ws.kpres.SlideContentProps
import ws.kpres.SlideInfos
import ws.utils.blockEffectFrom
import ws.utils.getValue
import ws.utils.kotlinSourceCode
import ws.utils.slideTitle

fun PresentationBuilder.thanks() {
    slide { child(ThankYou, it) }
    slide { slideTitle("One more thing!") }
    slide(SlideInfos(2)) { child(OneMoreThing, it) }
}

private val ThankYou by functionalComponent<SlideContentProps> { props ->
    styledH1 {
        css {
            margin(0.5.em)
        }
        +"Thank You!"
    }
    styledH2 {
        css {
            fontWeight = FontWeight.w200
            marginTop = 0.em
        }
        +"Romain Boisselle"

        styledA(href = "https://twitter.com/romainbsl", target = "_blank") {
            css {
                fontSize = 0.6.em
                fontWeight = FontWeight.w400
                color = Color("#007bfa")
                textDecoration = TextDecoration.none
                display = Display.block
                marginBottom = 0.75.em
            }
            +"@romainbsl"
        }
        styledA(href = "https://twitter.com/KodeinKoders", target = "_blank") {
            css {
                fontSize = 0.6.em
                fontWeight = FontWeight.w400
                color = Color("#007bfa")
                textDecoration = TextDecoration.none
                display = Display.block
                marginBottom = 1.75.em
            }
            +"@KodeinKoders"
        }
        styledA(href = "https://romainbsl.github.io/deck-advanced-di-on-mpp/", target = "_blank") {
            css {
                fontSize = 0.6.em
                fontWeight = FontWeight.w400
                color = Color.white
                textDecoration = TextDecoration.none
                display = Display.block
                marginBottom = 0.75.em
            }
            +"https://romainbsl.github.io/deck-advanced-di-on-mpp/"
        }
    }
}
private val OneMoreThing by functionalComponent<SlideContentProps> { props ->
    slideTitle("Win a JetBrains licence!")

    kotlinSourceCode("""
        fun main() {
            val s: String? = null
            if (s?.isEmpty()) println("is empty")«correct-msg«
            Type mismatch: inferred type «correct-msg«is» Boolean? 
            but Boolean was expected...
            »if (s.isNullOrEmpty()) println("is null or empty")
        }
        
        // What will it display? Some possibilities:
        a) is empty is null or empty
        b) is null or empty
        c) prints nothing
        «correct«d) doesn’t compile»
        """.trimIndent()) {
        +"c-correct" {
            if (props.state == 1) {
                color = Color.green
                fontWeight = FontWeight.w500
            }
        }
        +"c-correct-msg" {
            blockEffectFrom(props.state, 1)
            if (props.state == 1) {
                color = Color.darkRed
                fontWeight = FontWeight.w500
            }
        }
    }
}