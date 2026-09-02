package ai.deepcode.android.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudflareSettingsScreen(
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val securePrefs = repository.securePrefs

    var cloudflareToken by remember { mutableStateOf(securePrefs.getApiKey("cloudflare")) }
    var cloudflareSecondary by remember { mutableStateOf(securePrefs.getSetting("api_key_cloudflare_2", "")) }
    var cloudflareAccountId by remember { mutableStateOf(securePrefs.getSetting("cloudflare_account_id", "")) }
    var tokenVisible by remember { mutableStateOf(false) }
    var secondaryVisible by remember { mutableStateOf(false) }

    val imageModels = listOf(
        "@cf/lykon/dreamshaper-8-lcm" to "DreamShaper 8 LCM",
        "@cf/stabilityai/stable-diffusion-xl-base-1.0" to "SDXL Base 1.0",
        "@cf/bytedance/stable-diffusion-xl-lightning" to "SDXL Lightning",
        "@cf/black-forest-labs/flux-2-klein-4b" to "FLUX.2 Klein 4B",
        "@cf/black-forest-labs/flux-2-klein-9b" to "FLUX.2 Klein 9B",
        "@cf/black-forest-labs/flux-2-dev" to "FLUX.2 Dev"
    )
    var selectedModel by remember { mutableStateOf(securePrefs.getSetting("cloudflare_image_model", imageModels.first().first)) }
    var modelExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cloudflare AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = "Configure Cloudflare credentials for image generation.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Account ID
                    OutlinedTextField(
                        value = cloudflareAccountId,
                        onValueChange = { cloudflareAccountId = it },
                        label = { Text("Account ID") },
                        placeholder = { Text("Your Cloudflare Account ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Primary Token
                    OutlinedTextField(
                        value = cloudflareToken,
                        onValueChange = { cloudflareToken = it },
                        label = { Text("API Token") },
                        placeholder = { Text("Your Cloudflare API token (cfut_...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (tokenVisible) "Hide token" else "Show token",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Secondary Token
                    OutlinedTextField(
                        value = cloudflareSecondary,
                        onValueChange = { cloudflareSecondary = it },
                        label = { Text("Secondary Token (optional)") },
                        placeholder = { Text("Optional fallback API token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (secondaryVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { secondaryVisible = !secondaryVisible }) {
                                Icon(
                                    imageVector = if (secondaryVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Image Model selector
                    Column {
                        Text(
                            "Image Model",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box {
                            OutlinedTextField(
                                value = imageModels.firstOrNull { it.first == selectedModel }?.second ?: selectedModel,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { modelExpanded = !modelExpanded }) {
                                        Icon(
                                            imageVector = if (modelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                imageModels.forEach { (id, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 13.sp) },
                                        onClick = {
                                            selectedModel = id
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedAppButton(
                onClick = {
                    securePrefs.saveApiKey("cloudflare", cloudflareToken.trim())
                    securePrefs.saveSetting("api_key_cloudflare_2", cloudflareSecondary.trim())
                    securePrefs.saveSetting("cloudflare_account_id", cloudflareAccountId.trim())
                    securePrefs.saveSetting("cloudflare_image_model", selectedModel)
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Default.Save,
                text = "Save Settings",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
