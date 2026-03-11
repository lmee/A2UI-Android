package org.a2ui.compose.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.a2ui.compose.theme.*

/**
 * 增强的动画Card组件
 */
@Composable
fun AnimatedCard(
    modifier: Modifier = Modifier,
    themeConfig: A2UIThemeConfig = a2uiThemeConfig(),
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(themeConfig.borderRadius.dp)

    AnimatedVisibility(
        visible = visible,
        enter = if (themeConfig.enableAnimations) {
            createCardEnterTransition(themeConfig)
        } else {
            fadeIn(tween(0))
        },
        exit = if (themeConfig.enableAnimations) {
            createCardExitTransition(themeConfig)
        } else {
            fadeOut(tween(0))
        }
    ) {
        Card(
            modifier = modifier
                .glassmorphism(themeConfig, cardShape)
                .then(
                    if (themeConfig.enableAnimations) {
                        Modifier.animateContentSize(createAnimationSpec(themeConfig))
                    } else {
                        Modifier
                    }
                ),
            elevation = getEnhancedCardElevation(themeConfig),
            colors = getEnhancedCardColors(themeConfig),
            shape = cardShape
        ) {
            content()
        }
    }
}

/**
 * 带数据变化动画的文本组件
 */
@Composable
fun AnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    themeConfig: A2UIThemeConfig = a2uiThemeConfig()
) {
    var targetText by remember { mutableStateOf(text) }
    val animatedValue by animateFloatAsState(
        targetValue = if (targetText == text) 1f else 0f,
        animationSpec = if (themeConfig.enableDataTransitions) {
            createAnimationSpec(themeConfig)
        } else {
            tween(0)
        },
        label = "text_animation"
    )

    LaunchedEffect(text) {
        if (text != targetText) {
            targetText = text
        }
    }

    Text(
        text = targetText,
        style = style,
        modifier = modifier.then(
            if (themeConfig.enableDataTransitions) {
                Modifier.graphicsLayer {
                    scaleX = animatedValue
                    scaleY = animatedValue
                    alpha = animatedValue
                }
            } else {
                Modifier
            }
        )
    )
}

/**
 * 带微交互效果的按钮
 */
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    themeConfig: A2UIThemeConfig = a2uiThemeConfig(),
    content: @Composable RowScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = if (themeConfig.enableMicroInteractions) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        } else {
            tween(0)
        },
        label = "button_scale"
    )

    Button(
        onClick = {
            if (themeConfig.enableMicroInteractions) {
                isPressed = true
            }
            onClick()
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        content = content
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

/**
 * 列表项交错动画
 */
@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    themeConfig: A2UIThemeConfig = a2uiThemeConfig(),
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (themeConfig.enableAnimations && themeConfig.listItemAnimation == ListAnimation.STAGGER) {
            kotlinx.coroutines.delay((index * 100L).coerceAtMost(500L))
        }
        visible = true
    }

    val enterTransition = when (themeConfig.listItemAnimation) {
        ListAnimation.STAGGER -> slideInVertically(
            animationSpec = createAnimationSpec(themeConfig)
        ) { it / 2 } + fadeIn(createAnimationSpec(themeConfig))
        ListAnimation.WAVE -> slideInHorizontally(
            animationSpec = createAnimationSpec(themeConfig)
        ) { -it / 2 } + fadeIn(createAnimationSpec(themeConfig))
        ListAnimation.CASCADE -> scaleIn(
            animationSpec = createAnimationSpec(themeConfig),
            initialScale = 0.8f
        ) + fadeIn(createAnimationSpec(themeConfig))
        else -> fadeIn(tween(0))
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (themeConfig.enableAnimations) enterTransition else fadeIn(tween(0))
    ) {
        Box(modifier = modifier) {
            content()
        }
    }
}

/**
 * 数值变化动画组件
 */
@Composable
fun AnimatedNumber(
    targetValue: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    themeConfig: A2UIThemeConfig = a2uiThemeConfig(),
    formatter: (Float) -> String = { "%.2f".format(it) }
) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = if (themeConfig.enableDataTransitions) {
            createAnimationSpec(themeConfig)
        } else {
            tween(0)
        },
        label = "number_animation"
    )

    Text(
        text = formatter(animatedValue),
        style = style,
        modifier = modifier
    )
}

/**
 * 进度条动画组件
 */
@Composable
fun AnimatedProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    themeConfig: A2UIThemeConfig = a2uiThemeConfig()
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (themeConfig.enableDataTransitions) {
            createAnimationSpec(themeConfig)
        } else {
            tween(0)
        },
        label = "progress_animation"
    )

    LinearProgressIndicator(
        progress = animatedProgress,
        modifier = modifier
    )
}