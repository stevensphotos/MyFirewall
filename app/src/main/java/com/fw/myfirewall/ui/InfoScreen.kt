package com.fw.myfirewall.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fw.myfirewall.R
import com.fw.myfirewall.ui.theme.MyFirewallTheme
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoPage(
    onBack: () -> Unit = {},
    onClose: () -> Unit = {}
) {

    val context = LocalContext.current // <--- AJOUTER CETTE LIGNE ICI
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.information_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.save_rules_back_desc)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Section Fonctionnement
            InfoSectionCard(
                title = stringResource(R.string.info_how_it_works_title),
                icon = Icons.Rounded.Security,
                description = stringResource(R.string.info_how_it_works_desc)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section DNS
            InfoSectionCard(
                title = stringResource(R.string.info_dns_filtering_title),
                icon = Icons.Rounded.Description,
                description = stringResource(R.string.info_dns_filtering_desc)
            )
            Spacer(modifier = Modifier.height(16.dp))


            // Section HOSTS
            InfoSectionCard(
                title = stringResource(R.string.info_how_it_hosts_title),
                icon = Icons.Rounded.Description,
                description = stringResource(R.string.info_how_it_hosts_works_desc)
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://adblock.turtlecute.org".toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
            ) {
                Icon(Icons.Rounded.Public, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.test_url_filter))
            }

            Spacer(modifier = Modifier.height(16.dp))



            // Section QUICK FAQ
            InfoSectionCard(
                title = stringResource(R.string.info_faq_title),
                icon = Icons.Rounded.QuestionAnswer,
                description = stringResource(R.string.info_faq_issue)

            )

            Spacer(modifier = Modifier.height(24.dp))


            // Section Open Source / Support
            InfoSectionCard(
                title = stringResource(R.string.info_open_source_title),
                icon = Icons.Rounded.BugReport,
                description = stringResource(R.string.info_open_source_desc)
            )

            Spacer(modifier = Modifier.height(24.dp))





// SECTION REPOSITORY (Lien Web)
            InfoSectionCard(
                title = stringResource(R.string.info_support_title),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                description = stringResource(R.string.info_support_desc)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val intent =
                        Intent(Intent.ACTION_VIEW, "https://github.com/votre_repo".toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
            ) {
                Icon(Icons.Rounded.Public, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.info_button_repo))
            }

            // Close Button
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Text(text = stringResource(R.string.info_close_button))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun InfoSectionCard(title: String, icon: ImageVector, description: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "InfoPage Preview")
@Composable
fun InfoPagePreview() {
    MyFirewallTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            InfoPage()
        }
    }
}
