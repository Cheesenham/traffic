package com.trafficgauge.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trafficgauge.app.routing.PoiResult

@Composable
fun DestinationSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searchResults: List<PoiResult>,
    onResultSelected: (PoiResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("목적지 입력") },
                    singleLine = true,
                )
                Button(onClick = onSearch) { Text("검색") }
            }

            if (searchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    items(searchResults) { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultSelected(result) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(result.name, style = MaterialTheme.typography.bodyLarge)
                            result.address?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
