package org.a2ui.compose.rendering

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import coil.compose.AsyncImage
import org.a2ui.compose.data.*
import org.a2ui.compose.theme.A2UITheme
import org.a2ui.compose.theme.A2UIThemeConfig
import org.a2ui.compose.theme.parseColor

class ComponentRegistry(private val renderer: A2UIRenderer) {
    private val components = mutableMapOf<String, @Composable (Component, SurfaceContext) -> Unit>()
    private var customComponents = mutableMapOf<String, @Composable (Component, SurfaceContext) -> Unit>()

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
        customComponents[component.component]?.invoke(component, context)
            ?: components[component.component]?.invoke(component, context)
            ?: renderDefault(component, context)
    }

    private fun registerDefaultComponents() {
        register("Text") { component, context ->
            val text = renderer.resolveValue(context.surfaceId, component.text) as? String ?: ""
            val variant = component.variant

            val textStyle = when (variant) {
                "h1" -> MaterialTheme.typography.headlineLarge
                "h2" -> MaterialTheme.typography.headlineMedium
                "h3" -> MaterialTheme.typography.headlineSmall
                "title" -> MaterialTheme.typography.titleLarge
                "subtitle" -> MaterialTheme.typography.titleMedium
                "body" -> MaterialTheme.typography.bodyLarge
                "caption" -> MaterialTheme.typography.bodySmall
                "label" -> MaterialTheme.typography.labelLarge
                else -> MaterialTheme.typography.bodyLarge
            }

            Text(
                text = text,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip
            )
        }

        register("Button") { component, context ->
            val text = renderer.resolveValue(context.surfaceId, component.text) as? String ?: ""
            val variant = component.variant
            val isEnabled = true
            val interactionSource = remember { MutableInteractionSource() }

            val buttonModifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = text
                    role = Role.Button
                    stateDescription = if (isEnabled) "Enabled" else "Disabled"
                }

            when (variant) {
                "secondary", "outlined" -> {
                    OutlinedButton(
                        onClick = { component.action?.let { renderer.handleAction(context.surfaceId, it) } },
                        enabled = isEnabled,
                        modifier = buttonModifier,
                        interactionSource = interactionSource
                    ) {
                        Text(text = text)
                    }
                }
                "text", "borderless" -> {
                    TextButton(
                        onClick = { component.action?.let { renderer.handleAction(context.surfaceId, it) } },
                        enabled = isEnabled,
                        modifier = buttonModifier,
                        interactionSource = interactionSource
                    ) {
                        Text(text = text)
                    }
                }
                else -> {
                    Button(
                        onClick = { component.action?.let { renderer.handleAction(context.surfaceId, it) } },
                        enabled = isEnabled,
                        modifier = buttonModifier,
                        interactionSource = interactionSource
                    ) {
                        Text(text = text)
                    }
                }
            }
        }

        register("Row") { component, context ->
            val justifyContent = when (component.justify) {
                "start" -> Arrangement.Start
                "center" -> Arrangement.Center
                "end" -> Arrangement.End
                "spaceBetween" -> Arrangement.SpaceBetween
                "spaceAround" -> Arrangement.SpaceAround
                "spaceEvenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.Start
            }

            val alignItems = when (component.align) {
                "start" -> Alignment.Top
                "center" -> Alignment.CenterVertically
                "end" -> Alignment.Bottom
                "stretch" -> Alignment.Stretch
                else -> Alignment.Top
            }

            val modifier = if (component.weight != null) {
                Modifier.weight(component.weight!!.toFloat()).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }

            Row(
                modifier = modifier,
                horizontalArrangement = justifyContent,
                verticalAlignment = alignItems
            ) {
                renderChildren(component, context)
            }
        }

        register("Column") { component, context ->
            val justifyContent = when (component.justify) {
                "start" -> Arrangement.Top
                "center" -> Arrangement.Center
                "end" -> Arrangement.Bottom
                "spaceBetween" -> Arrangement.SpaceBetween
                "spaceAround" -> Arrangement.SpaceAround
                "spaceEvenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.Top
            }

            val alignItems = when (component.align) {
                "start" -> Alignment.Start
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                "stretch" -> Alignment.Stretch
                else -> Alignment.Start
            }

            val modifier = if (component.weight != null) {
                Modifier.weight(component.weight!!.toFloat()).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }

            Column(
                modifier = modifier,
                verticalArrangement = justifyContent,
                horizontalAlignment = alignItems
            ) {
                renderChildren(component, context)
            }
        }

        register("TextField") { component, context ->
            val label = renderer.resolveValue(context.surfaceId, component.label) as? String ?: ""
            val value = renderer.resolveValue(context.surfaceId, component.value) as? String ?: ""
            val placeholder = renderer.resolveValue(context.surfaceId, component.placeholder) as? String ?: ""
            val variant = component.variant
            val checks = component.checks
            val required = component.required ?: false
            val minLength = component.minLength
            val maxLength = component.maxLength
            val pattern = component.pattern

            var text by rememberSaveable { mutableStateOf(value) }
            var isError by rememberSaveable { mutableStateOf(false) }
            var errorMessage by rememberSaveable { mutableStateOf("") }
            var hasBeenTouched by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(value) {
                if (text != value) {
                    text = value
                }
            }

            fun validate(input: String): Pair<Boolean, String> {
                if (required && input.isBlank()) {
                    return Pair(false, "This field is required")
                }

                if (minLength != null && input.length < minLength) {
                    return Pair(false, "Minimum length is $minLength characters")
                }

                if (maxLength != null && input.length > maxLength) {
                    return Pair(false, "Maximum length is $maxLength characters")
                }

                if (pattern != null && input.isNotEmpty()) {
                    try {
                        if (!Regex(pattern).matches(input)) {
                            return Pair(false, "Invalid format")
                        }
                    } catch (e: Exception) {
                        // Invalid regex pattern, skip validation
                    }
                }

                checks?.forEach { check ->
                    val isValid = when (check.call) {
                        "required" -> input.isNotBlank()
                        "email" -> {
                            if (input.isEmpty()) true
                            else isValidEmail(input)
                        }
                        "url" -> {
                            if (input.isEmpty()) true
                            else isValidUrl(input)
                        }
                        "phone" -> {
                            if (input.isEmpty()) true
                            else isValidPhone(input)
                        }
                        "minLength" -> {
                            val min = (check.args["min"] as? Number)?.toInt() ?: 0
                            input.length >= min
                        }
                        "maxLength" -> {
                            val max = (check.args["max"] as? Number)?.toInt() ?: Int.MAX_VALUE
                            input.length <= max
                        }
                        "regex" -> {
                            val regexPattern = check.args["pattern"] as? String ?: ""
                            if (input.isEmpty() || regexPattern.isEmpty()) true
                            else try {
                                Regex(regexPattern).matches(input)
                            } catch (e: Exception) {
                                true
                            }
                        }
                        "numeric" -> {
                            input.toDoubleOrNull() != null
                        }
                        else -> true
                    }

                    if (!isValid) {
                        return Pair(false, check.message ?: "Invalid value")
                    }
                }

                return Pair(true, "")
            }

            val isSingleLine = variant == "shortText"

            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue
                    hasBeenTouched = true
                    
                    val (valid, error) = validate(newValue)
                    isError = !valid
                    errorMessage = error

                    component.value?.let { dynamicValue ->
                        if (dynamicValue is DynamicValue.PathValue) {
                            renderer.updateDataModel(context.surfaceId, dynamicValue.path, newValue)
                        }
                    }
                },
                label = { 
                    Text(
                        text = if (required) "$label *" else label
                    ) 
                },
                placeholder = { Text(text = placeholder) },
                isError = isError && hasBeenTouched,
                supportingText = if (isError && hasBeenTouched) {
                    { 
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            }
                        ) 
                    }
                } else null,
                singleLine = isSingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = buildString {
                            append(label)
                            if (required) append(", required field")
                            if (isError && hasBeenTouched) append(", error: $errorMessage")
                        }
                    }
            )
        }

        register("CheckBox") { component, context ->
            val label = renderer.resolveValue(context.surfaceId, component.label) as? String ?: ""
            val value = renderer.resolveValue(context.surfaceId, component.value) as? Boolean ?: false

            var checked by rememberSaveable { mutableStateOf(value) }

            LaunchedEffect(value) {
                if (checked != value) {
                    checked = value
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newValue = !checked
                        checked = newValue
                        component.value?.let { dynamicValue ->
                            if (dynamicValue is DynamicValue.PathValue) {
                                renderer.updateDataModel(context.surfaceId, dynamicValue.path, newValue)
                            }
                        }
                    }
                    .padding(vertical = 8.dp)
                    .semantics {
                        contentDescription = label
                        role = Role.Checkbox
                        stateDescription = if (checked) "Checked" else "Not checked"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { newValue ->
                        checked = newValue
                        component.value?.let { dynamicValue ->
                            if (dynamicValue is DynamicValue.PathValue) {
                                renderer.updateDataModel(context.surfaceId, dynamicValue.path, newValue)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label)
            }
        }

        register("Card") { component, context ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                component.child?.let { childId ->
                    renderer.getComponent(context.surfaceId, childId)?.let {
                        Box(modifier = Modifier.padding(16.dp)) {
                            render(it, context)
                        }
                    }
                }
            }
        }

        register("Surface") { component, context ->
            component.child?.let { childId ->
                renderer.getComponent(context.surfaceId, childId)?.let {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        render(it, context)
                    }
                }
            }
        }

        register("Image") { component, context ->
            val url = renderer.resolveValue(context.surfaceId, component.url) as? String ?: ""
            val altText = renderer.resolveValue(context.surfaceId, component.text) as? String ?: "Image"

            if (url.isNotEmpty()) {
                var imageLoaded by remember { mutableStateOf(false) }
                var loadError by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = altText,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onSuccess = { imageLoaded = true },
                        onError = { loadError = true }
                    )

                    if (!imageLoaded && !loadError) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    if (loadError) {
                        Text(
                            text = "Failed to load image",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        register("Video") { component, context ->
            val url = renderer.resolveValue(context.surfaceId, component.url) as? String ?: ""

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play Video",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Video: $url",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        register("AudioPlayer") { component, context ->
            val url = renderer.resolveValue(context.surfaceId, component.url) as? String ?: ""

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Audio: $url",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        register("Icon") { component, context ->
            val name = renderer.resolveValue(context.surfaceId, component.text) as? String ?: "star"

            val iconVector = when (name) {
                "star" -> Icons.Default.Star
                "mail" -> Icons.Default.Mail
                "phone" -> Icons.Default.Phone
                "home" -> Icons.Default.Home
                "settings" -> Icons.Default.Settings
                "search" -> Icons.Default.Search
                "menu" -> Icons.Default.Menu
                "close" -> Icons.Default.Close
                "check" -> Icons.Default.Check
                "add" -> Icons.Default.Add
                "remove" -> Icons.Default.Remove
                "arrow_back" -> Icons.Default.ArrowBack
                "arrow_forward" -> Icons.Default.ArrowForward
                "play_arrow" -> Icons.Default.PlayArrow
                "pause" -> Icons.Default.Pause
                "stop" -> Icons.Default.Stop
                "person" -> Icons.Default.Person
                "favorite" -> Icons.Default.Favorite
                "info" -> Icons.Default.Info
                "warning" -> Icons.Default.Warning
                "error" -> Icons.Default.Error
                "success" -> Icons.Default.CheckCircle
                "edit" -> Icons.Default.Edit
                "delete" -> Icons.Default.Delete
                "save" -> Icons.Default.Save
                "refresh" -> Icons.Default.Refresh
                "more_vert" -> Icons.Default.MoreVert
                "more_horiz" -> Icons.Default.MoreHoriz
                "expand_more" -> Icons.Default.ExpandMore
                "expand_less" -> Icons.Default.ExpandLess
                "chevron_right" -> Icons.Default.ChevronRight
                "chevron_left" -> Icons.Default.ChevronLeft
                else -> Icons.Default.Star
            }

            Icon(
                imageVector = iconVector,
                contentDescription = name,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        register("Divider") { _, _ ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        register("Slider") { component, context ->
            val value = renderer.resolveValue(context.surfaceId, component.value) as? Double ?: 0.0
            val min = component.min ?: 0.0
            val max = component.max ?: 100.0
            val step = component.step ?: 1.0
            val label = renderer.resolveValue(context.surfaceId, component.label) as? String

            var sliderValue by rememberSaveable { mutableStateOf(value.toFloat()) }

            LaunchedEffect(value) {
                if (sliderValue != value.toFloat()) {
                    sliderValue = value.toFloat()
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (label != null) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        sliderValue = newValue
                    },
                    onValueChangeFinished = {
                        component.value?.let { dynamicValue ->
                            if (dynamicValue is DynamicValue.PathValue) {
                                renderer.updateDataModel(context.surfaceId, dynamicValue.path, sliderValue.toDouble())
                            }
                        }
                    },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = if (step > 0 && (max - min) / step > 1) ((max - min) / step).toInt() - 1 else 0,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = String.format("%.1f", sliderValue),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        register("ChoicePicker") { component, context ->
            val value = renderer.resolveValue(context.surfaceId, component.value)
            val options = component.options ?: emptyList()
            val multiple = component.multiple ?: false

            var selectedValue by rememberSaveable { mutableStateOf(value) }

            LaunchedEffect(value) {
                if (selectedValue != value) {
                    selectedValue = value
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    val isSelected = if (multiple) {
                        (selectedValue as? List<*>)?.contains(option.value) ?: false
                    } else {
                        selectedValue == option.value
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newValue = if (multiple) {
                                    val currentList = (selectedValue as? List<*>)?.toMutableList() ?: mutableListOf()
                                    if (isSelected) {
                                        currentList.remove(option.value)
                                    } else {
                                        currentList.add(option.value)
                                    }
                                    currentList
                                } else {
                                    option.value
                                }

                                selectedValue = newValue
                                component.value?.let { dynamicValue ->
                                    if (dynamicValue is DynamicValue.PathValue) {
                                        renderer.updateDataModel(context.surfaceId, dynamicValue.path, newValue)
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (multiple) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null
                            )
                        } else {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option.label)
                    }
                }
            }
        }

        register("List") { component, context ->
            when (val children = component.children) {
                is ChildList.ObjectChildList -> {
                    val dataItems = renderer.resolveValue(context.surfaceId, DynamicValue.PathValue<Any>(children.objectChild.path)) as? List<*>
                    val templateId = children.objectChild.componentId

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dataItems?.let { items ->
                            itemsIndexed(
                                items = items,
                                key = { index, item -> 
                                    when (item) {
                                        is Map<*, *> -> item["id"]?.hashCode() ?: index
                                        else -> item?.hashCode() ?: index
                                    }
                                }
                            ) { index, item ->
                                renderer.getComponent(context.surfaceId, templateId)?.let { template ->
                                    key(index) {
                                        render(template, context)
                                    }
                                }
                            }
                        }
                    }
                }
                is ChildList.ArrayChildList -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = children.array,
                            key = { index, childId -> childId.hashCode() }
                        ) { index, childId ->
                            renderer.getComponent(context.surfaceId, childId)?.let {
                                key(childId) {
                                    render(it, context)
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        register("Tabs") { component, context ->
            var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
            val tabs = component.options ?: emptyList()

            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, option ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(text = option.label) }
                        )
                    }
                }

                Box(modifier = Modifier.padding(16.dp)) {
                    val selectedTab = tabs.getOrNull(selectedTabIndex)
                    selectedTab?.let { tab ->
                        component.child?.let { childId ->
                            renderer.getComponent(context.surfaceId, childId)?.let {
                                render(it, context)
                            }
                        }
                    }
                }
            }
        }

        register("Modal") { component, context ->
            var isVisible by rememberSaveable { mutableStateOf(true) }

            if (isVisible) {
                Dialog(
                    onDismissRequest = {
                        isVisible = false
                        component.action?.let { action ->
                            if (action.event?.name == "dismiss") {
                                renderer.handleAction(context.surfaceId, action)
                            }
                        }
                    },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                scaleIn(initialScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessLow)),
                        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + 
                               scaleOut(targetScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .wrapContentHeight(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    component.child?.let { childId ->
                                        renderer.getComponent(context.surfaceId, childId)?.let {
                                            render(it, context)
                                        }
                                    }
                                    IconButton(onClick = { isVisible = false }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        register("DateTimeInput") { component, context ->
            val label = renderer.resolveValue(context.surfaceId, component.label) as? String ?: ""
            val value = renderer.resolveValue(context.surfaceId, component.value) as? String ?: ""

            var text by rememberSaveable { mutableStateOf(value) }
            var showDatePicker by rememberSaveable { mutableStateOf(false) }

            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue
                    component.value?.let { dynamicValue ->
                        if (dynamicValue is DynamicValue.PathValue) {
                            renderer.updateDataModel(context.surfaceId, dynamicValue.path, newValue)
                        }
                    }
                },
                label = { Text(text = label) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Pick date")
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
                                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    .format(java.util.Date(millis))
                                text = date
                                component.value?.let { dynamicValue ->
                                    if (dynamicValue is DynamicValue.PathValue) {
                                        renderer.updateDataModel(context.surfaceId, dynamicValue.path, date)
                                    }
                                }
                            }
                            showDatePicker = false
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
        }

        register("Spacer") { component, context ->
            val height = component.min?.toInt()?.dp ?: 8.dp
            Spacer(modifier = Modifier.height(height))
        }

        register("ProgressBar") { component, context ->
            val progress = renderer.resolveValue(context.surfaceId, component.value) as? Double

            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        register("Switch") { component, context ->
            val label = renderer.resolveValue(context.surfaceId, component.label) as? String ?: ""
            val value = renderer.resolveValue(context.surfaceId, component.value) as? Boolean ?: false

            var checked by rememberSaveable { mutableStateOf(value) }

            LaunchedEffect(value) {
                if (checked != value) {
                    checked = value
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newValue = !checked
                        checked = newValue
                        component.value?.let { dynamicValue ->
                            if (dynamicValue is DynamicValue.PathValue) {
                                renderer.updateDataModel(context.surfaceId, dynamicValue.path, newValue)
                            }
                        }
                    }
                    .padding(vertical = 8.dp)
                    .semantics {
                        contentDescription = label
                        role = Role.Switch
                        stateDescription = if (checked) "On" else "Off"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label)
                Switch(
                    checked = checked,
                    onCheckedChange = { newValue ->
                        checked = newValue
                        component.value?.let { dynamicValue ->
                            if (dynamicValue is DynamicValue.PathValue) {
                                renderer.updateDataModel(context.surfaceId, dynamicValue.path, newValue)
                            }
                        }
                    }
                )
            }
        }

        register("Dropdown") { component, context ->
            val value = renderer.resolveValue(context.surfaceId, component.value)
            val options = component.options ?: emptyList()
            val label = renderer.resolveValue(context.surfaceId, component.label) as? String ?: ""

            var expanded by rememberSaveable { mutableStateOf(false) }
            var selectedOption by rememberSaveable { mutableStateOf(options.find { it.value == value }) }

            LaunchedEffect(value) {
                val newOption = options.find { it.value == value }
                if (selectedOption != newOption) {
                    selectedOption = newOption
                }
            }

            Column {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                OutlinedTextField(
                    value = selectedOption?.label ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option.label) },
                            onClick = {
                                selectedOption = option
                                expanded = false
                                component.value?.let { dynamicValue ->
                                    if (dynamicValue is DynamicValue.PathValue) {
                                        renderer.updateDataModel(context.surfaceId, dynamicValue.path, option.value)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun renderChildren(component: Component, context: SurfaceContext) {
        when (val children = component.children) {
            is ChildList.ArrayChildList -> {
                children.array.forEach { childId ->
                    renderer.getComponent(context.surfaceId, childId)?.let {
                        render(it, context)
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
        val dataItems = renderer.resolveValue(context.surfaceId, DynamicValue.PathValue<Any>(template.path)) as? List<*>

        dataItems?.let { items ->
            items.forEachIndexed { index, _ ->
                renderer.getComponent(context.surfaceId, template.componentId)?.let { templateComponent ->
                    render(templateComponent, context)
                }
            }
        }
    }

    @Composable
    private fun renderDefault(component: Component, context: SurfaceContext) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
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

private fun <T> DynamicValue(path: String): DynamicValue<T> {
    return DynamicValue.PathValue(path)
}

private fun isValidEmail(email: String): Boolean {
    if (email.isBlank()) return false
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex(RegexOption.IGNORE_CASE)
    return emailRegex.matches(email)
}

private fun isValidUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val urlRegex = "^(https?://)?([\\w.-]+)(\\.[\\w.-]+)+[/#?]?.*$".toRegex(RegexOption.IGNORE_CASE)
    return urlRegex.matches(url)
}

private fun isValidPhone(phone: String): Boolean {
    if (phone.isBlank()) return false
    val phoneRegex = "^[+]?[0-9]{10,15}$".toRegex()
    val cleanedPhone = phone.replace(Regex("[\\s-()]+"), "")
    return phoneRegex.matches(cleanedPhone)
}
