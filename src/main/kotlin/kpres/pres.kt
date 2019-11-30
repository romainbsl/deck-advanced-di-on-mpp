package ws.kpres

import kotlinext.js.jsObject
import kotlinx.css.*
import kotlinx.css.properties.*
import kotlinx.html.tabIndex
import org.w3c.dom.BroadcastChannel
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.url.URLSearchParams
import react.*
import react.dom.h1
import react.router.dom.hashRouter
import react.router.dom.route
import styled.css
import styled.styledDiv
import ws.utils.getValue
import ws.utils.provideDelegate
import kotlin.browser.window
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


typealias SlideHandler = RElementBuilder<SlideProps>.(SlideContentProps) -> Unit

data class SlideInfos(
        val stateCount: Int = 1,
        val containerStyle: CSSBuilder.(Int) -> Unit = {},
        val inTransitions: Transition.Set? = null,
        val outTransitions: Transition.Set? = null,
        val inTransitionDuration: Int? = null,
        val debugAlign: Boolean = false
) {
    init {
        require(stateCount >= 1)
    }
}

private interface RouteProps: RProps {
    val slide: String?
    val state: String?
}

private enum class Mode {
    OVERVIEW,
    PRESENTER
}

private fun Set<Mode>.params() = if (isEmpty()) "" else "mode=" + joinToString(separator = ",") { it.name.toLowerCase() }

private data class SlidePosition(val slide: Int, val state: Int)

sealed class TransitionState {
    abstract val forward: Boolean
    class Prepare(override val forward: Boolean) : TransitionState()
    class Execute(val state: Int, val duration: Int, override val forward: Boolean, val remaining: Int) : TransitionState()
}

private fun useTransitionState(getTransitionDuration: (Boolean) -> Int, getTransition: (Boolean) -> Transition): Pair<TransitionState?, (Boolean) -> Unit> {
    var transitionState by useState<TransitionState?>(null)

    useEffectWithCleanup(listOf(transitionState)) {
        val state = transitionState
        val timerId = when (state) {
            is TransitionState.Prepare -> {
                val transitionDuration = getTransitionDuration(state.forward)
                val stateDuration = getTransition(state.forward).stateDuration(transitionDuration, 0).takeIf { it in 0..transitionDuration } ?: transitionDuration
                window.setTimeout({ transitionState = TransitionState.Execute(0, stateDuration, state.forward, transitionDuration - stateDuration) }, 1)
            }
            is TransitionState.Execute -> {
                when (state.remaining) {
                    0 -> window.setTimeout({ transitionState = null }, state.duration)
                    else -> {
                        val stateDuration = getTransition(state.forward).stateDuration(state.remaining, state.state + 1).takeIf { it in 0..state.remaining } ?: state.remaining
                        window.setTimeout({ transitionState = TransitionState.Execute(state.state + 1, stateDuration, state.forward, state.remaining - stateDuration) }, state.duration)
                    }
                }
            }
            null -> null
        }
        if (timerId != null) ({ window.clearTimeout(timerId) })
        else ({})
    }

    return transitionState to { forward: Boolean -> transitionState = TransitionState.Prepare(forward) }
}

private class SlideRender(
        val currentPosition: SlidePosition,
        val previousPosition: SlidePosition,
        val appearState: TransitionState?,
        val disappearState: TransitionState?
)

private class SlideContainerProps(val presProps: PresentationProps, val position: SlidePosition, val style: CSSBuilder.() -> Unit, val render: RBuilder.(SlideRender) -> Unit) : RProps
private val slideContainer by functionalComponent<SlideContainerProps> { props ->
    var currentPosition by useState(props.position)
    var previousPosition by useState(SlidePosition(0, 0))

    val getTransitionDuration = { forward: Boolean -> (if (forward) props.presProps.slideInfos(currentPosition) else props.presProps.slideInfos(previousPosition))?.inTransitionDuration ?: props.presProps.defaultTransitionDuration }
    val (appearState, startAppear) = useTransitionState(getTransitionDuration) { props.presProps.transitionSet(currentPosition) { if (it) inTransitions else outTransitions }.appear }
    val (disappearState, startDisappear) = useTransitionState(getTransitionDuration) { props.presProps.transitionSet(previousPosition) { if (it) outTransitions else inTransitions }.disappear }

    useEffect(listOf(props.position.slide, props.position.state)) {
        if (currentPosition.slide != props.position.slide) {
            val forward = props.position.slide > currentPosition.slide
            startAppear(forward)
            startDisappear(forward)
            previousPosition = currentPosition
        }
        currentPosition = props.position
    }

    styledDiv {
        this.key = "container"
        css {
            +"pres-container"
            position = Position.absolute
            top = 0.pct
            left = 0.pct
            width = 100.pct
            height = 100.pct
            overflow = Overflow.hidden
            (props.style)()
        }

        styledDiv {
            css {
                +"inner-container"
                width = 100.pct
                height = 100.pct
                position = Position.relative
            }

            (props.render)(SlideRender(currentPosition, previousPosition, appearState, disappearState))
        }
    }
}

private class PresentationProps(
        val slides: List<Pair<SlideInfos, SlideHandler>>,
        val position: SlidePosition,
        val defaultTransitions: Transition.Set,
        val defaultTransitionDuration: Int,
        val modes: Set<Mode>
) : RProps

private fun PresentationProps.slideInfos(index: SlidePosition) = index.slide.takeIf { it in 0..slides.lastIndex } ?.let { slides[it].first }
private fun PresentationProps.transitionSet(index: SlidePosition, select: SlideInfos.() -> Transition.Set?) = slideInfos(index)?.select() ?: defaultTransitions

private val Presentation by functionalComponent<PresentationProps> { props ->
    val containerDiv = useRef<HTMLDivElement?>(null)
    useEffect(emptyList()) {
        containerDiv.current!!.focus()
    }

    val channel by useState { BroadcastChannel("pres-pos") }

    var noBroadcast by useState(false)

    useEffectWithCleanup {
        channel.onmessage = {
            val newPos = it.data.unsafeCast<SlidePosition>()
            if (props.position != newPos) {
                noBroadcast = true
                window.location.href = "#/${newPos.slide}/${newPos.state}?${props.modes.params()}"
            }
            Unit
        }
        ({ channel.onmessage = null })
    }

    useEffect(listOf(props.position.slide, props.position.state)) {
        if (!noBroadcast) {
            channel.postMessage(props.position)
        }
        noBroadcast = false
    }

    fun getNextPosition() = when {
        props.position.state < (props.slides[props.position.slide].first.stateCount - 1) -> SlidePosition(props.position.slide, props.position.state + 1)
        props.position.slide < props.slides.lastIndex -> SlidePosition(props.position.slide + 1, 0)
        else -> props.position
    }
    fun getPreviousPos() = when {
        props.position.state > 0 -> SlidePosition(props.position.slide, props.position.state - 1)
        props.position.slide > 0 -> SlidePosition(props.position.slide - 1, props.slides[props.position.slide - 1].first.stateCount - 1)
        else -> SlidePosition(0, 0)
    }

    useEffect {
        containerDiv.current!!.onkeydown = {
            when (it.keyCode) {
                32, 34, 39, 40 -> {
                    when {
                        it.ctrlKey || Mode.OVERVIEW in props.modes -> {
                            if (props.position.slide < props.slides.lastIndex) {
                                window.location.href = "#/${props.position.slide + 1}/0?${props.modes.params()}"
                            }
                        }
                        else -> with(getNextPosition()) { window.location.href = "#/$slide/$state?${props.modes.params()}" }
                    }
                }
                33, 37, 38 -> {
                    when {
                        it.ctrlKey || Mode.OVERVIEW in props.modes -> {
                            if (props.position.slide > 0) {
                                window.location.href = "#/${props.position.slide - 1}/0?${props.modes.params()}"
                            }
                        }
                        else -> with(getPreviousPos()) { window.location.href = "#/$slide/$state?${props.modes.params()}" }
                    }
                }
                27 -> {
                    val modes = if (Mode.OVERVIEW in props.modes) props.modes - Mode.OVERVIEW else props.modes + Mode.OVERVIEW
                    window.location.href = "#/${props.position.slide}/${props.position.state}?${modes.params()}"
                }
                13 -> {
                    if (Mode.OVERVIEW in props.modes) window.location.href = "#/${props.position.slide}/${props.position.state}?${(props.modes - Mode.OVERVIEW).params()}"
                }
                80 -> {
                    if (it.ctrlKey && Mode.PRESENTER !in props.modes) {
                        window.open("#/${props.position.slide}/${props.position.state}?mode=presenter")
                    } else {
                        val modes = if (Mode.PRESENTER in props.modes) props.modes - Mode.PRESENTER else props.modes + Mode.PRESENTER
                        window.location.href = "#/${props.position.slide}/${props.position.state}?${modes.params()}"
                    }
                }
            }
            Unit
        }
    }

    styledDiv {
        ref = containerDiv
        attrs.tabIndex = "0"
        css {
            +"k-pres"
            position = Position.relative
            width = 100.pct
            height = 100.pct
            outline = Outline.none
            overflow = Overflow.hidden
        }

        fun RBuilder.slide(position: SlidePosition, transition: Transition?, transitionState: TransitionState?, keyPostfix: String = ""/*, overviewDelta: Int? = null*/) {
            styledDiv {
                if (position.slide != -1) key = "slide-${position.slide}$keyPostfix"

                css {
                    width = 100.pct
                    height = 100.pct
                    this.position = Position.absolute
                    top = 0.pct
                    left = 0.pct
                    if (transition != null) {
                        when (transitionState) {
                            is TransitionState.Prepare -> {
                                with(transition) { prepare(transitionState.forward) }
                            }
                            is TransitionState.Execute -> {
                                with(transition) { execute(transitionState.state, transitionState.duration, transitionState.forward) }
                            }
                        }
                    }
                }

                val slidePair = position.slide.takeIf { it >= 0 } ?.let { props.slides[it] }
                if (slidePair != null) {
                    val (slideInfos, slideHandler) = slidePair
                    child(functionalComponent = Slide, props = jsObject { this.state = position.state }) {
                        val shouldAnim = transitionState == null
                                || (transitionState.forward && position.state != 0)
                                || (!transitionState.forward && position.state != slideInfos.stateCount - 1)
                        slideHandler(SlideContentProps(position.state, shouldAnim))
                    }
                }
            }
        }

        when  {
            Mode.OVERVIEW in props.modes -> {
                for (i in max(0, props.position.slide - 2)..min(props.slides.lastIndex, props.position.slide + 2)) {
                    val infos = props.slides[i].first
                    child(
                            functionalComponent = slideContainer,
                            props = SlideContainerProps(
                                    presProps = props,
                                    position = when {
                                        i == props.position.slide -> props.position
                                        i < props.position.slide -> SlidePosition(i, infos.stateCount - 1)
                                        else -> SlidePosition(i, 0)
                                    },
                                    style = {
                                        universal { put("transition", "none !important") }

                                        infos.containerStyle.let {
                                            specific {
                                                it(if (i < props.position.slide) infos.stateCount - 1 else 0)
                                            }
                                        }

                                        val overviewDelta = i - props.position.slide
                                        transform {
                                            if (overviewDelta == 0) {
                                                scale(0.26)
                                                zIndex = 1
                                            } else {
                                                translateX((23 * overviewDelta + 2 * (overviewDelta / abs(overviewDelta))).pct)
                                                scale(0.22)
                                            }
                                        }

                                        boxShadow(Color.black, blurRadius = 1.2.em, spreadRadius = if (overviewDelta == 0) 0.5.em else 0.em)
                                    }
                            ) {
                                slide(it.currentPosition, null, null, keyPostfix = "$i"/*, overviewDelta = i - currentPosition.slide*/)
                            }
                    ) {
                        key = "container-$i"
                    }
                }
            }
            Mode.PRESENTER in props.modes -> {
                child(
                        functionalComponent = slideContainer,
                        props = SlideContainerProps(
                                presProps = props,
                                position = props.position,
                                style = {
                                    put("transform-origin", "top left")
                                    transform {
                                        translate(2.pct, 2.pct)
                                        scale(0.50)
                                    }
                                    props.slideInfos(props.position)?.containerStyle?.let {
                                        specific {
                                            it(props.position.state)
                                        }
                                    }
                                    boxShadow(Color.black, blurRadius = 1.2.em, spreadRadius = 0.5.em)
                                }
                        ) {
                            if (it.disappearState != null) {
                                val transitionSet = props.transitionSet(it.previousPosition) { if (it.disappearState.forward) outTransitions else inTransitions }
                                slide(it.previousPosition, transitionSet.disappear, it.disappearState)
                            }

                            val transitionSet = props.transitionSet(it.currentPosition) { if (it.appearState?.forward == true) inTransitions else outTransitions }
                            slide(it.currentPosition, transitionSet.appear, it.appearState)
                        }
                ) {
                    key = "container-c"
                }
                val nextPosition = getNextPosition()
                child(
                        functionalComponent = slideContainer,
                        props = SlideContainerProps(
                                presProps = props,
                                position = nextPosition,
                                style = {
                                    put("transform-origin", "bottom left")
                                    transform {
                                        translate(4.5.pct, (-2).pct)
                                        scale(0.44)
                                    }
                                    props.slideInfos(nextPosition)?.containerStyle?.let {
                                        specific {
                                            it(nextPosition.state)
                                        }
                                    }
                                    boxShadow(Color.black, blurRadius = 1.2.em)
                                }
                        ) {
                            if (it.disappearState != null) {
                                val transitionSet = props.transitionSet(it.previousPosition) { if (it.disappearState.forward) outTransitions else inTransitions }
                                slide(it.previousPosition, transitionSet.disappear, it.disappearState)
                            }

                            val transitionSet = props.transitionSet(it.currentPosition) { if (it.appearState?.forward == true) inTransitions else outTransitions }
                            slide(it.currentPosition, transitionSet.appear, it.appearState)
                        }
                ) {
                    key = "container-n"
                }
            }
            else -> {
                child(
                        slideContainer,
                        SlideContainerProps(
                                presProps = props,
                                position = props.position,
                                style = {
                                    props.slideInfos(props.position)?.containerStyle?.let {
                                        specific {
                                            it(props.position.state)
                                        }
                                    }
                                }
                        ) {
                            if (props.slideInfos(it.currentPosition)?.debugAlign == true || it.disappearState != null) {
                                val transitionSet = props.transitionSet(it.previousPosition) { if (it.disappearState?.forward == true) outTransitions else inTransitions }
                                slide(it.previousPosition, transitionSet.disappear, it.disappearState)
                            }

                            val transitionSet = props.transitionSet(it.currentPosition) { if (it.appearState?.forward == true) inTransitions else outTransitions }
                            slide(it.currentPosition, transitionSet.appear, it.appearState)
                        }
                ) {
                    key = "container"
                }
            }
        }
    }
}

interface PresentationBuilder {
    fun slide(infos: SlideInfos = SlideInfos(), handler: SlideHandler)
}

fun RBuilder.presentation(
        defaultTransition: Transition.Set = Fade,
        transitionDuration: Int = 500,
        builder: PresentationBuilder.() -> Unit
) {
    val slides = ArrayList<Pair<SlideInfos, SlideHandler>>()
    object : PresentationBuilder {
        override fun slide(infos: SlideInfos, handler: SlideHandler) {
            slides += infos to handler
        }
    }.builder()

    if (slides.isEmpty()) {
        h1 { +"No slides!" }
        return
    }

    hashRouter {
            route<RouteProps>("/:slide?/:state?") {
                val slide = it.match.params.slide?.toIntOrNull() ?: 0
                val state = it.match.params.state?.toIntOrNull() ?: 0
                val modes = URLSearchParams(it.location.search).get("mode")
                        ?.split(",")
                        ?.mapNotNull { try { Mode.valueOf(it.toUpperCase()) } catch (_: Throwable) { null } }
                        ?.toSet()
                        ?: emptySet()
                child(functionalComponent = Presentation, props = PresentationProps(slides, SlidePosition(slide, state), defaultTransition, transitionDuration, modes))
            }
//        }
    }
}