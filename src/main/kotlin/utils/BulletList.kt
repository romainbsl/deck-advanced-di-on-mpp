package ws.utils

import kotlinx.css.*
import kotlinx.css.properties.ms
import kotlinx.css.properties.transition
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import react.*
import react.dom.div
import react.dom.li
import styled.*
import ws.kpres.SlideContentProps
import ws.kpres.sourceCode


class BulletListProps(
        baseProps: SlideContentProps,
        val style: RuleSet = {},
        val block: RBuilder.() -> Unit
): SlideContentProps(baseProps.state, baseProps.shouldAnim)

private val BulletList by functionalComponent<BulletListProps> {props ->
    val ul = useRef<HTMLElement?>(null)

    useEffect(listOf(props.state)) {
        val codes = ul.current!!.querySelectorAll("div.code").asList()
        codes.forEach { (it as HTMLElement).style.height = "0" }
        if (props.state > 0 && props.state <= codes.size) {
            val code = codes[props.state -1] as HTMLElement
            code.style.height = "calc(${(code.firstChild!! as HTMLElement).clientHeight}px + 1em)"
        }
    }

    styledUl {
        ref = ul
        css {
            margin(0.em)
            display = Display.flex
            flexDirection = FlexDirection.column
            alignSelf = Align.stretch
            height = 14.em
            "li" {
                "span" {
                    opacity = 1.0
                    transition(::opacity, 300.ms)
                }
                "pre" {
                    margin(0.5.em, 2.em)
                    transition(::height, 300.ms)
                }
            }
            "div.code" {
                height = 0.px
                overflow = Overflow.hidden
                if (props.shouldAnim) {
                    transition(::height, 300.ms)
                }
            }
            props.style
        }

        props.block(this)
    }
}

fun RBuilder.bulletList(props: SlideContentProps, style: RuleSet = {}, block: RBuilder.() -> Unit) =
        child(functionalComponent = BulletList,
        props = BulletListProps(props, style, block))

fun RBuilder.bulletPoint(currentState: Int, stateRef: Int, value: String, level: Int = 1, ruleSet: CSSBuilder.(currentState: Int, stateRef: Int) -> Unit = stepByStepBulletRule) {
    styledLi {
        css {
            margin(0.5.em)
            marginLeft = (level * 1).em
            "span" {
                opacity = 1.0
                transition(::opacity, 300.ms)
            }
        }

        styledSpan {
            css { ruleSet(currentState, stateRef) }
            +value
        }
    }
}

fun RBuilder.bulletCode(currentState: Int, stateRef: Int, name: String, lang: String, code: String) {
    li {
        styledSpan {
            css {
                specific {
                    if (currentState < stateRef) opacity = 0.0
                    else if (currentState in (stateRef + 1)..6) opacity = 0.25
                    position = Position.relative
                }
            }
            +name
        }

        div("code") {
            sourceCode(lang, code)
        }
    }
}

val stepByStepBulletRule: CSSBuilder.(Int, Int) -> Unit = { state, thisState ->
    specific {
        if (state < thisState) opacity = 0.0
    }
}

fun versusBulletRule(count: Int = 1): CSSBuilder.(Int, Int) -> Unit = { state, thisState ->
    specific {
        if (state < thisState) opacity = 0.0
        else if (thisState <= state - count) opacity = 0.25
    }
}