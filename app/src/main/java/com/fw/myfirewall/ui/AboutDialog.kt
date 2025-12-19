package com.fw.myfirewall.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialogDefaults.containerColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fw.myfirewall.R
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import java.security.MessageDigest

/**
 * AboutDialog: Comprehensive "About" section matching the provided layout while preserving authenticity logic.
 */
@Composable
fun AboutDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }
    
    val signature = remember { getAppSignature(context) }
    val officialSignature = "63:DE:21:77:79:9A:FC:94:E3:12:E3:10:52:B0:5B:99:17:35:33:26:EF:18:8C:A8:DA:7F:6E:4A:A2:5D:88:E8"
    val isAuthentic = signature.replace(":", "")
        .equals(officialSignature.replace(":", ""), ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.95f) // Utilise 95% de la largeur de l'écran
            .padding(vertical = 96.dp), // Optionnel : marge haut/bas
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.ok))

            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. APP ICON
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                }

                // 2. TITLE
                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // 3. DESCRIPTION
                Text(
                    text = stringResource(R.string.settings_about_description),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        hyphens = Hyphens.Auto
                    ),
                    textAlign = TextAlign.Justify,
                    lineHeight = 20.sp
                )

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // 4. CERTIFICATE SECTION
                Text(
                    text = stringResource(R.string.certificate_authenticity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (isAuthentic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = signature,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = stringResource(R.string.SHA_256_Fingerprint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                // 5. BUILD INFO
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BuildInfoRow(label = "Package", value = context.packageName)
                    BuildInfoRow(label = "Version", value = packageInfo?.versionName ?: "Unknown")
                    val buildCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo?.longVersionCode?.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo?.versionCode?.toString()
                    }
                    BuildInfoRow(label = "Build", value = buildCode ?: "Unknown")
                }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // 6. LINKS SECTION
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinkRow(icon = Icons.Rounded.Code, label = stringResource(R.string.about_github)) {}
                    LinkRow(icon = Icons.Rounded.HelpOutline, label = stringResource(R.string.about_help)) {}
                    LinkRow(icon = Icons.Rounded.BugReport, label = stringResource(R.string.about_bugs)) {}
                    LinkRow(icon = Icons.Rounded.Description, label = stringResource(R.string.about_licence)) {}
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun BuildInfoRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(22.dp),


            ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = buildAnnotatedString {
                append(label)
                append(" ")
                withStyle(style = SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)) {
                    append("link")
                }
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun getAppSignature(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        val md = MessageDigest.getInstance("SHA-256")
        val signatureBytes = signatures?.get(0)?.toByteArray()
        if (signatureBytes != null) {
            val digest = md.digest(signatureBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } else {
            "Signature unavailable"
        }
    } catch (_: Exception) {
        "Signature unavailable"
    }
}

@Preview(showBackground = true)
@Composable
fun AboutDialogPreview() {
    MyFirewallTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AboutDialog(onDismissRequest = {})
        }
    }
}
