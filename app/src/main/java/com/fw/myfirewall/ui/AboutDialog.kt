package com.fw.myfirewall.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fw.myfirewall.R
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import java.security.MessageDigest

/**
 * AboutDialog: Comprehensive "About" section with Authenticity Certificate.
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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(text = stringResource(R.string.settings_about_title))
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_about_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        hyphens = Hyphens.Auto
                    ),
                    textAlign = TextAlign.Justify,
                    lineHeight = 20.sp
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // AUTHENTICITY CERTIFICATE SECTION
                val isAuthentic = signature.replace(":", "")
                    .equals(officialSignature.replace(":", ""), ignoreCase = true)
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.certificate_authenticity_title),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isAuthentic) MaterialTheme.colorScheme.primary else
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )

                    // SHA-256 Fingerprint
                    Text(
                        text = stringResource(R.string.SHA_256_Fingerprint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = signature,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            lineHeight = 14.sp
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAuthentic)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CertificateRow(label = "Package", value = context.packageName)
                            CertificateRow(label = "Version", value = packageInfo?.versionName ?: "Unknown")
                            val buildCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                packageInfo?.longVersionCode?.toString()
                            } else {
                                @Suppress("DEPRECATION")
                                packageInfo?.versionCode?.toString()
                            }
                            CertificateRow(label = "Build", value = buildCode ?: "Unknown")

                            Spacer(Modifier.height(4.dp))

                            // Links Section
                            CertificateRow(
                                label = stringResource(R.string.about_github),
                                value = "GitHub"
                            )
                            CertificateRow(
                                label = stringResource(R.string.about_help),
                                value = "Help"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.ok))
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun CertificateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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


//@Preview(showBackground = true)
//@Composable
//fun AboutDialogPreview() {
//    MyFirewallTheme {
//        Box(modifier = Modifier.padding(16.dp)) {
//            AboutDialog(onDismissRequest = {})
//        }
//    }
//}

