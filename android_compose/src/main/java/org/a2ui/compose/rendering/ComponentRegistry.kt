package org.a2ui.compose.rendering

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import coil.compose.AsyncImage
import org.a2ui.compose.data.*
import org.a2ui.compose.theme.A2UITheme
import org.a2ui.compose.theme.A2UIThemeConfig
import org.a2ui.compose.theme.parseColor
import org.a2ui.compose.validation.SafeRegexValidator
import java.util.concurrent.ConcurrentHashMap

class ComponentRegistry(private val renderer: A2UIRenderer) {
    private val components = ConcurrentHashMap<String, @Composable (Component, SurfaceContext) -> Unit>()
    private val customComponents = ConcurrentHashMap<String, @Composable (Component, SurfaceContext) -> Unit>()

    companion object {
        private const val MAX_RENDER_DEPTH = 50
    }

    init {
        registerDefaultComponents()
    }

    fun register(componentName: String, factory: @Composable (Component, SurfaceContext) -> Unit) {
        components[componentName] = factory
    }

    fun registerCustomComponent(componentName: String, factory: @Composable (Component, SurfaceContext) -> Unit) {
        customComponents[componentName] = factory
    }

    fun unregisterCustomComponent(componentName: String) {
        customComponents.remove(componentName)
    }

    @Composable
    fun render(component: Component, context: SurfaceContext) {
        if (context.renderDepth > MAX_RENDER_DEPTH) {
            renderDepthError(component)
            return
        }
        customComponents[component.component]?.invoke(component, context)
            ?: components[component.component]?.invoke(component, context)
            ?: renderDefault(component, context)
    }

    @Composable
    private fun renderDepthError(component: Component) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Render depth limit exceeded: ${component.component}",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    /** 解析值，支持 collection scope */
    private fun resolve(ctx: SurfaceContext, value: DynamicValue<*>?): Any? {
        return renderer.resolveValueWithScope(ctx.surfaceId, value, ctx.scopePath)
    }

    private fun registerDefaultComponents() {
        // ==================== Text ====================
        register("Text") { component, context ->
            val text = resolve(context, component.text) as? String ?: ""
            // v0.9: variant, v0.8: usageHint
            val hint = component.variant ?: component.usageHint
            val textStyle = when (hint) {
                "h1" -> MaterialTheme.typography.headlineLarge
                "h2" -> MaterialTheme.typography.headlineMedium
                "h3" -> MaterialTheme.typography.headlineSmall
                "h4" -> MaterialTheme.typography.titleLarge
                "h5" -> MaterialTheme.typography.titleMedium
                "title" -> MaterialTheme.typography.titleLarge
                "subtitle" -> MaterialTheme.typography.titleMedium
                "body" -> MaterialTheme.typography.bodyLarge
                "caption" -> MaterialTheme.typography.bodySmall
                "label" -> MaterialTheme.typography.labelLarge
                else -> MaterialTheme.typography.bodyLarge
            }
            Text(text = text, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
        }

        // ==================== Button ====================
        // v0.9: child (ComponentId) + action, variant: primary/borderless
        // v0.8: child (ComponentId) + action, primary: boolean
        register("Button") { component, context ->
            val isPrimary = component.variant == "primary" || component.primary == true
            val isBorderless = component.variant == "borderless" || component.variant == "text"
            val isEnabled = (resolve(context, component.value) as? Boolean) ?: true

            val buttonModifier = Modifier.fillMaxWidth().semantics { role = Role.Button }

            val onClick: () -> Unit = {
                component.action?.let { renderer.handleAction(context.surfaceId, it) }
            }

            // 渲染按钮内容：优先通过 child 引用子组件，fallback 到 text
            val buttonContent: @Composable RowScope.() -> Unit = {
                val childId = component.child
                if (childId != null) {
                    renderer.getComponent(context.surfaceId, childId)?.let { childComp ->
                        render(childComp, context.copy(renderDepth = context.renderDepth + 1))
                    }
                } else {
                    val text = resolve(context, component.text) as? String ?: ""
                    Text(text = text)
                }
            }

            when {
                isBorderless -> TextButton(onClick = onClick, enabled = isEnabled, modifier = buttonModifier, content = buttonContent)
                isPrimary -> Button(onClick = onClick, enabled = isEnabled, modifier = buttonModifier, content = buttonContent)
                else -> OutlinedButton(onClick = onClick, enabled = isEnabled, modifier = buttonModifier, content = buttonContent)
            }
        }

        // ==================== Row ====================
        register("Row") { component, context ->
            val justifyContent = when (component.justify) {
                "start" -> Arrangement.Start; "center" -> Arrangement.Center
                "end" -> Arrangement.End; "spaceBetween" -> Arrangement.SpaceBetween
                "spaceAround" -> Arrangement.SpaceAround; "spaceEvenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.Start
            }
            val alignItems = when (component.align) {
                "start" -> Alignment.Top; "center" -> Alignment.CenterVertically
                "end" -> Alignment.Bottom; else -> Alignment.Top
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = justifyContent,
                verticalAlignment = alignItems
            ) { renderChildren(component, context) }
        }

        // ==================== Column ====================
        register("Column") { component, context ->
            val justifyContent = when (component.justify) {
                "start" -> Arrangement.Top; "center" -> Arrangement.Center
                "end" -> Arrangement.Bottom; "spaceBetween" -> Arrangement.SpaceBetween
                "spaceAround" -> Arrangement.SpaceAround; "spaceEvenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.Top
            }
            val alignItems = when (component.align) {
                "start" -> Alignment.Start; "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End; else -> Alignment.Start
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = justifyContent,
                horizontalAlignment = alignItems
            ) { renderChildren(component, context) }
        }

        // ==================== TextField ====================
        register("TextField") { component, context ->
            val label = resolve(context, component.label) as? String ?: ""
            val value = resolve(context, component.value) as? String ?: ""
            val placeholder = resolve(context, component.placeholder) as? String ?: ""
            // v0.9: variant (shortText/longText/number/obscured), v0.8: textFieldType
            val variant = component.variant ?: component.textFieldType
            val checks = component.checks
            val required = component.required ?: false

            val keyboardType = when (variant) {
                "number" -> KeyboardType.Number
                "obscured", "password" -> KeyboardType.Password
                else -> KeyboardType.Text
            }
            val visualTransformation = if (variant == "obscured" || variant == "password")
                PasswordVisualTransformation() else VisualTransformation.None

            var text by rememberSaveable { mutableStateOf(value) }
            var isError by rememberSaveable { mutableStateOf(false) }
            var errorMessage by rememberSaveable { mutableStateOf("") }
            var hasBeenTouched by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(value) { if (text != value) text = value }

            fun validate(input: String): Pair<Boolean, String> {
                if (required && input.isBlank()) return Pair(false, "This field is required")
                // validationRegexp (v0.8/v0.9)
                val regexpPattern = component.validationRegexp ?: component.pattern
                if (regexpPattern != null && input.isNotEmpty()) {
                    val matched = SafeRegexValidator.safeMatchesBlocking(regexpPattern, input)
                    if (matched == false) return Pair(false, "Invalid format")
                    if (matched == null) return Pair(false, "Invalid pattern")
                }
                checks?.forEach { check ->
                    val isValid = when (check.call) {
                        "required" -> input.isNotBlank()
                        "email" -> input.isEmpty() || isValidEmail(input)
                        "regex" -> {
                            val p = check.args["pattern"] as? String ?: ""
                            input.isEmpty() || (SafeRegexValidator.safeMatchesBlocking(p, input) ?: false)
                        }
                        else -> true
                    }
                    if (!isValid) return Pair(false, check.message ?: "Invalid value")
                }
                return Pair(true, "")
            }

            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue; hasBeenTouched = true
                    val (valid, error) = validate(newValue)
                    isError = !valid; errorMessage = error
                    component.value?.let { dv ->
                        if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, newValue)
                    }
                },
                label = { Text(text = if (required) "$label *" else label) },
                placeholder = { Text(text = placeholder) },
                isError = isError && hasBeenTouched,
                supportingText = if (isError && hasBeenTouched) {
                    { Text(text = errorMessage, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                } else null,
                singleLine = variant == "shortText",
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = buildString {
                        append(label)
                        if (required) append(", required field")
                        if (isError && hasBeenTouched) append(", error: $errorMessage")
                    }
                }
            )
        }

        // ==================== CheckBox ====================
        register("CheckBox") { component, context ->
            val label = resolve(context, component.label) as? String ?: ""
            val value = resolve(context, component.value) as? Boolean ?: false
            var checked by rememberSaveable { mutableStateOf(value) }
            LaunchedEffect(value) { if (checked != value) checked = value }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        val nv = !checked; checked = nv
                        component.value?.let { dv ->
                            if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, nv)
                        }
                    }
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = label; role = Role.Checkbox },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = { nv ->
                    checked = nv
                    component.value?.let { dv ->
                        if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, nv)
                    }
                })
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label)
            }
        }

        // ==================== Card ====================
        register("Card") { component, context ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                // ✅ 同时支持 child 和 children
                val childId = component.child
                val children = component.children
                when {
                    childId != null -> {
                        renderer.getComponent(context.surfaceId, childId)?.let {
                            Box(modifier = Modifier.padding(16.dp)) {
                                render(it, context.copy(renderDepth = context.renderDepth + 1))
                            }
                        }
                    }
                    children != null -> {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Column { resolveChildren(component, context) }
                        }
                    }
                }
            }
        }

        // ==================== Image ====================
        register("Image") { component, context ->
            val url = resolve(context, component.url) as? String ?: ""
            val altText = resolve(context, component.text) as? String ?: "Image"
            // v0.9: fit, variant; v0.8: fit, usageHint
            val fitMode = component.fit
            val imgVariant = component.variant ?: component.usageHint

            val contentScale = when (fitMode) {
                "contain" -> ContentScale.Fit
                "cover" -> ContentScale.Crop
                "fill" -> ContentScale.FillBounds
                "none" -> ContentScale.None
                "scale-down" -> ContentScale.Inside
                else -> ContentScale.Crop
            }

            val (imgModifier, imgShape) = when (imgVariant) {
                "icon" -> Modifier.size(24.dp) to RoundedCornerShape(4.dp)
                "avatar" -> Modifier.size(48.dp) to CircleShape
                "smallFeature" -> Modifier.fillMaxWidth().height(120.dp) to RoundedCornerShape(8.dp)
                "mediumFeature" -> Modifier.fillMaxWidth().height(200.dp) to RoundedCornerShape(8.dp)
                "largeFeature" -> Modifier.fillMaxWidth().height(300.dp) to RoundedCornerShape(8.dp)
                "header" -> Modifier.fillMaxWidth().height(250.dp) to RoundedCornerShape(0.dp)
                else -> Modifier.fillMaxWidth().height(200.dp) to RoundedCornerShape(8.dp)
            }

            if (url.isNotEmpty()) {
                // ✅ URL scheme 校验
                val isAllowed = A2UIRenderer.ALLOWED_URL_SCHEMES.any { url.startsWith(it) }
                if (!isAllowed) return@register

                Box(modifier = imgModifier.clip(imgShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    AsyncImage(
                        model = url, contentDescription = altText,
                        modifier = Modifier.fillMaxSize(), contentScale = contentScale
                    )
                }
            }
        }

        // ==================== Icon ====================
        // v0.9: name 属性, v0.8: name 属性 (literalString)
        // fallback: text 属性（向后兼容旧实现）
        register("Icon") { component, context ->
            val iconName = resolve(context, component.name) as? String
                ?: resolve(context, component.text) as? String
                ?: "star"

            val iconVector = ICON_MAP[iconName] ?: Icons.Default.Star

            Icon(
                imageVector = iconVector,
                contentDescription = iconName,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // ==================== Divider ====================
        // v0.9: axis (horizontal/vertical)
        register("Divider") { component, _ ->
            val axis = component.axis ?: "horizontal"
            if (axis == "vertical") {
                VerticalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp).height(24.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            } else {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        // ==================== Slider ====================
        register("Slider") { component, context ->
            val value = (resolve(context, component.value) as? Number)?.toFloat() ?: 0f
            val min = (component.min ?: 0.0).toFloat()
            val max = (component.max ?: 100.0).toFloat()
            val label = resolve(context, component.label) as? String

            var sliderValue by rememberSaveable { mutableStateOf(value) }
            LaunchedEffect(value) { if (sliderValue != value) sliderValue = value }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (label != null) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        component.value?.let { dv ->
                            if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, sliderValue.toDouble())
                        }
                    },
                    valueRange = min..max,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = String.format("%.1f", sliderValue),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        // ==================== ChoicePicker ====================
        // v0.9: variant (mutuallyExclusive/multipleSelection), options, value (DynamicStringList)
        // v0.8: MultipleChoice with selections, maxAllowedSelections
        register("ChoicePicker") { component, context ->
            val currentValue = resolve(context, component.value)
            val options = component.options ?: emptyList()
            val isMultiple = component.variant == "multipleSelection" || component.multiple == true
            val label = resolve(context, component.label) as? String

            var selectedValues by rememberSaveable {
                mutableStateOf(
                    when (currentValue) {
                        is List<*> -> currentValue.map { it.toString() }.toSet()
                        is String -> setOf(currentValue)
                        else -> emptySet()
                    }
                )
            }

            LaunchedEffect(currentValue) {
                val newSet = when (currentValue) {
                    is List<*> -> currentValue.map { it.toString() }.toSet()
                    is String -> setOf(currentValue)
                    else -> emptySet()
                }
                if (selectedValues != newSet) selectedValues = newSet
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (label != null) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                }
                options.forEach { option ->
                    val optVal = option.value.toString()
                    val isSelected = selectedValues.contains(optVal)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                val newSet = if (isMultiple) {
                                    if (isSelected) selectedValues - optVal else selectedValues + optVal
                                } else { setOf(optVal) }
                                selectedValues = newSet
                                val newValue: Any = if (isMultiple) newSet.toList() else newSet.first()
                                component.value?.let { dv ->
                                    if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, newValue)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isMultiple) Checkbox(checked = isSelected, onCheckedChange = null)
                        else RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option.label)
                    }
                }
            }
        }

        // ==================== List ====================
        // v0.9: children (array or template), direction (vertical/horizontal)
        register("List") { component, context ->
            val isHorizontal = component.direction == "horizontal"

            when (val children = component.children) {
                is ChildList.ObjectChildList -> {
                    val dataPath = children.objectChild.path
                    val dataItems = resolve(context, DynamicValue.PathValue<Any>(dataPath)) as? List<*>
                    val templateId = children.objectChild.componentId

                    if (isHorizontal) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dataItems?.let { items ->
                                itemsIndexed(items, key = { index, _ -> index }) { index, _ ->
                                    renderer.getComponent(context.surfaceId, templateId)?.let { template ->
                                        // ✅ Collection Scope: 传递 scopePath
                                        val itemScope = "$dataPath/$index"
                                        render(template, context.copy(
                                            renderDepth = context.renderDepth + 1,
                                            scopePath = itemScope
                                        ))
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dataItems?.let { items ->
                                itemsIndexed(items, key = { index, _ -> index }) { index, _ ->
                                    renderer.getComponent(context.surfaceId, templateId)?.let { template ->
                                        val itemScope = "$dataPath/$index"
                                        render(template, context.copy(
                                            renderDepth = context.renderDepth + 1,
                                            scopePath = itemScope
                                        ))
                                    }
                                }
                            }
                        }
                    }
                }
                is ChildList.ArrayChildList -> {
                    if (isHorizontal) {
                        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(children.array, key = { _, id -> id }) { _, childId ->
                                renderer.getComponent(context.surfaceId, childId)?.let {
                                    render(it, context.copy(renderDepth = context.renderDepth + 1))
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(children.array, key = { _, id -> id }) { _, childId ->
                                renderer.getComponent(context.surfaceId, childId)?.let {
                                    render(it, context.copy(renderDepth = context.renderDepth + 1))
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        // ==================== Tabs ====================
        // v0.9: tabs (array of {title, child})
        // v0.8: tabItems (array of {title, child})
        register("Tabs") { component, context ->
            val tabList = component.tabs ?: component.tabItems ?: emptyList()
            var selectedTabIndex by rememberSaveable { mutableStateOf(0) }

            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabList.forEachIndexed { index, tab ->
                        val title = resolve(context, tab.title) as? String ?: "Tab ${index + 1}"
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(text = title) }
                        )
                    }
                }
                Box(modifier = Modifier.padding(16.dp)) {
                    val selectedTab = tabList.getOrNull(selectedTabIndex)
                    selectedTab?.child?.let { childId ->
                        renderer.getComponent(context.surfaceId, childId)?.let {
                            render(it, context.copy(renderDepth = context.renderDepth + 1))
                        }
                    }
                }
            }
        }

        // ==================== Modal ====================
        // v0.9: trigger + content
        // v0.8: entryPointChild + contentChild
        register("Modal") { component, context ->
            val triggerId = component.trigger ?: component.entryPointChild
            val contentId = component.content ?: component.contentChild ?: component.child
            var isVisible by rememberSaveable { mutableStateOf(false) }

            // 渲染触发器
            triggerId?.let { id ->
                renderer.getComponent(context.surfaceId, id)?.let { triggerComp ->
                    Box(modifier = Modifier.clickable { isVisible = true }) {
                        render(triggerComp, context.copy(renderDepth = context.renderDepth + 1))
                    }
                }
            }

            // 渲染模态框
            if (isVisible && contentId != null) {
                Dialog(
                    onDismissRequest = { isVisible = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
                ) {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) + scaleIn(initialScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessLow)),
                        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = { isVisible = false }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                                        }
                                    }
                                    renderer.getComponent(context.surfaceId, contentId)?.let {
                                        render(it, context.copy(renderDepth = context.renderDepth + 1))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== DateTimeInput ====================
        register("DateTimeInput") { component, context ->
            val label = resolve(context, component.label) as? String ?: ""
            val value = resolve(context, component.value) as? String ?: ""
            // v0.9: enableDate/enableTime, v0.8: variant (date/time/datetime)
            val showDate = component.enableDate ?: (component.variant != "time")
            val showTime = component.enableTime ?: (component.variant == "time" || component.variant == "datetime")

            var text by rememberSaveable { mutableStateOf(value) }
            var showDatePicker by rememberSaveable { mutableStateOf(false) }
            var showTimePicker by rememberSaveable { mutableStateOf(false) }
            var pendingDate by rememberSaveable { mutableStateOf("") }

            fun commitValue(newValue: String) {
                text = newValue
                component.value?.let { dv ->
                    if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, newValue)
                }
            }

            OutlinedTextField(
                value = text, onValueChange = {},
                label = { Text(text = label) },
                modifier = Modifier.fillMaxWidth(), readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { if (showDate) showDatePicker = true else showTimePicker = true }) {
                        Icon(
                            imageVector = if (!showDate && showTime) Icons.Default.Schedule else Icons.Default.DateRange,
                            contentDescription = "Pick"
                        )
                    }
                }
            )

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(millis))
                                if (showTime) { pendingDate = date; showDatePicker = false; showTimePicker = true }
                                else { commitValue(date); showDatePicker = false }
                            }
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
                ) { DatePicker(state = datePickerState) }
            }

            if (showTimePicker) {
                val timePickerState = rememberTimePickerState()
                Dialog(onDismissRequest = { showTimePicker = false }) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            TimePicker(state = timePickerState)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                                TextButton(onClick = {
                                    val time = String.format(java.util.Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)
                                    commitValue(if (pendingDate.isNotEmpty()) "${pendingDate}T${time}:00" else time)
                                    showTimePicker = false
                                }) { Text("OK") }
                            }
                        }
                    }
                }
            }
        }

        // ==================== Video (placeholder) ====================
        register("Video") { component, context ->
            val url = resolve(context, component.url) as? String ?: ""
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = "Play Video", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Video: $url", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ==================== AudioPlayer (placeholder) ====================
        register("AudioPlayer") { component, context ->
            val url = resolve(context, component.url) as? String ?: ""
            val desc = resolve(context, component.description) as? String ?: url
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = desc, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ==================== Surface ====================
        register("Surface") { component, context ->
            component.child?.let { childId ->
                renderer.getComponent(context.surfaceId, childId)?.let {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        render(it, context.copy(renderDepth = context.renderDepth + 1))
                    }
                }
            }
        }

        // ==================== Spacer ====================
        register("Spacer") { component, _ ->
            val height = component.min?.toInt()?.dp ?: 8.dp
            Spacer(modifier = Modifier.height(height))
        }

        // ==================== ProgressBar ====================
        register("ProgressBar") { component, context ->
            val progress = (resolve(context, component.value) as? Number)?.toFloat()
            if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // ==================== Switch ====================
        register("Switch") { component, context ->
            val label = resolve(context, component.label) as? String ?: ""
            val value = resolve(context, component.value) as? Boolean ?: false
            var checked by rememberSaveable { mutableStateOf(value) }
            LaunchedEffect(value) { if (checked != value) checked = value }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        val nv = !checked; checked = nv
                        component.value?.let { dv ->
                            if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, nv)
                        }
                    }
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = label; role = Role.Switch },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label)
                Switch(checked = checked, onCheckedChange = { nv ->
                    checked = nv
                    component.value?.let { dv ->
                        if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, nv)
                    }
                })
            }
        }

        // ==================== Dropdown ====================
        register("Dropdown") { component, context ->
            val value = resolve(context, component.value)
            val options = component.options ?: emptyList()
            val label = resolve(context, component.label) as? String ?: ""
            var expanded by rememberSaveable { mutableStateOf(false) }
            var selectedOption by rememberSaveable { mutableStateOf(options.find { it.value == value }) }

            Column {
                if (label.isNotEmpty()) Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(
                    value = selectedOption?.label ?: "", onValueChange = {}, readOnly = true,
                    trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown") } },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option.label) },
                            onClick = {
                                selectedOption = option; expanded = false
                                component.value?.let { dv ->
                                    if (dv is DynamicValue.PathValue) renderer.updateDataModel(context.surfaceId, dv.path, option.value)
                                }
                            }
                        )
                    }
                }
            }
        }

        // v0.8 兼容: MultipleChoice -> ChoicePicker
        register("MultipleChoice") { component, context ->
            components["ChoicePicker"]?.invoke(component, context)
        }
    }

    /**
     * 公共子组件解析逻辑，供所有 renderChildren 重载和 Card 使用
     */
    @Composable
    private fun resolveChildren(component: Component, context: SurfaceContext) {
        when (val children = component.children) {
            is ChildList.ArrayChildList -> {
                children.array.forEach { childId ->
                    renderer.getComponent(context.surfaceId, childId)?.let {
                        val childWeight = it.weight
                        val modifier = if (childWeight != null && childWeight > 0) {
                            Modifier
                        } else Modifier
                        Box(modifier = modifier) {
                            render(it, context.copy(renderDepth = context.renderDepth + 1))
                        }
                    }
                }
            }
            is ChildList.ObjectChildList -> {
                renderListTemplate(context, children.objectChild)
            }
            else -> {}
        }
    }

    @Composable
    private fun renderChildren(component: Component, context: SurfaceContext) {
        when (val children = component.children) {
            is ChildList.ArrayChildList -> {
                children.array.forEach { childId ->
                    renderer.getComponent(context.surfaceId, childId)?.let {
                        Box {
                            render(it, context.copy(renderDepth = context.renderDepth + 1))
                        }
                    }
                }
            }
            is ChildList.ObjectChildList -> {
                renderListTemplate(context, children.objectChild)
            }
            else -> {}
        }
    }

    @Composable
    private fun RowScope.renderChildren(component: Component, context: SurfaceContext) {
        when (val children = component.children) {
            is ChildList.ArrayChildList -> {
                children.array.forEach { childId ->
                    renderer.getComponent(context.surfaceId, childId)?.let {
                        Box {
                            render(it, context.copy(renderDepth = context.renderDepth + 1))
                        }
                    }
                }
            }
            is ChildList.ObjectChildList -> {
                renderListTemplate(context, children.objectChild)
            }
            else -> {}
        }
    }

    @Composable
    private fun ColumnScope.renderChildren(component: Component, context: SurfaceContext) {
        when (val children = component.children) {
            is ChildList.ArrayChildList -> {
                children.array.forEach { childId ->
                    renderer.getComponent(context.surfaceId, childId)?.let {
                        Box {
                            render(it, context.copy(renderDepth = context.renderDepth + 1))
                        }
                    }
                }
            }
            is ChildList.ObjectChildList -> {
                renderListTemplate(context, children.objectChild)
            }
            else -> {}
        }
    }

    @Composable
    private fun renderListTemplate(context: SurfaceContext, template: ChildTemplate) {
        val dataPath = template.path
        val dataItems = resolve(context, DynamicValue.PathValue<Any>(dataPath)) as? List<*>

        dataItems?.let { items ->
            items.forEachIndexed { index, _ ->
                renderer.getComponent(context.surfaceId, template.componentId)?.let { templateComponent ->
                    // ✅ Collection Scope
                    val itemScope = "$dataPath/$index"
                    render(templateComponent, context.copy(
                        renderDepth = context.renderDepth + 1,
                        scopePath = itemScope
                    ))
                }
            }
        }
    }

    @Composable
    private fun renderDefault(component: Component, context: SurfaceContext) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Unsupported component: ${component.component}",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

// ==================== Icon Map (v0.9 standard catalog, camelCase) ====================
private val ICON_MAP = mapOf(
    "accountCircle" to Icons.Default.AccountCircle,
    "add" to Icons.Default.Add,
    "arrowBack" to Icons.Default.ArrowBack,
    "arrowForward" to Icons.Default.ArrowForward,
    "attachFile" to Icons.Default.AttachFile,
    "calendarToday" to Icons.Default.DateRange,
    "call" to Icons.Default.Call,
    "camera" to Icons.Default.CameraAlt,
    "check" to Icons.Default.Check,
    "close" to Icons.Default.Close,
    "delete" to Icons.Default.Delete,
    "download" to Icons.Default.Download,
    "edit" to Icons.Default.Edit,
    "event" to Icons.Default.Event,
    "error" to Icons.Default.Error,
    "fastForward" to Icons.Default.FastForward,
    "favorite" to Icons.Default.Favorite,
    "favoriteOff" to Icons.Default.FavoriteBorder,
    "folder" to Icons.Default.Folder,
    "help" to Icons.Default.Help,
    "home" to Icons.Default.Home,
    "info" to Icons.Default.Info,
    "locationOn" to Icons.Default.LocationOn,
    "lock" to Icons.Default.Lock,
    "lockOpen" to Icons.Default.LockOpen,
    "mail" to Icons.Default.Mail,
    "menu" to Icons.Default.Menu,
    "moreVert" to Icons.Default.MoreVert,
    "moreHoriz" to Icons.Default.MoreHoriz,
    "notificationsOff" to Icons.Default.NotificationsOff,
    "notifications" to Icons.Default.Notifications,
    "pause" to Icons.Default.Pause,
    "payment" to Icons.Default.Payment,
    "person" to Icons.Default.Person,
    "phone" to Icons.Default.Phone,
    "photo" to Icons.Default.Photo,
    "play" to Icons.Default.PlayArrow,
    "print" to Icons.Default.Print,
    "refresh" to Icons.Default.Refresh,
    "rewind" to Icons.Default.FastRewind,
    "search" to Icons.Default.Search,
    "send" to Icons.Default.Send,
    "settings" to Icons.Default.Settings,
    "share" to Icons.Default.Share,
    "shoppingCart" to Icons.Default.ShoppingCart,
    "skipNext" to Icons.Default.SkipNext,
    "skipPrevious" to Icons.Default.SkipPrevious,
    "star" to Icons.Default.Star,
    "starHalf" to Icons.Default.StarHalf,
    "starOff" to Icons.Default.StarBorder,
    "stop" to Icons.Default.Stop,
    "upload" to Icons.Default.Upload,
    "visibility" to Icons.Default.Visibility,
    "visibilityOff" to Icons.Default.VisibilityOff,
    "volumeDown" to Icons.Default.VolumeDown,
    "volumeMute" to Icons.Default.VolumeMute,
    "volumeOff" to Icons.Default.VolumeOff,
    "volumeUp" to Icons.Default.VolumeUp,
    "warning" to Icons.Default.Warning,
    // legacy snake_case fallbacks
    "arrow_back" to Icons.Default.ArrowBack,
    "arrow_forward" to Icons.Default.ArrowForward,
    "play_arrow" to Icons.Default.PlayArrow,
    "more_vert" to Icons.Default.MoreVert,
    "more_horiz" to Icons.Default.MoreHoriz,
)

private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex(RegexOption.IGNORE_CASE)

private fun isValidEmail(email: String): Boolean {
    if (email.isBlank()) return false
    return EMAIL_REGEX.matches(email)
}
