package ios.silv.gemclient.ui.components

import androidx.compose.material3.SearchBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController


@PreviewLightDark
@Composable
private fun PreviewBottomSearchBar() {
    BottomSearchBar(
        rememberNavController(),
        onTabViewClick = {}
    )
}

@Composable
fun BottomSearchBar(
    navController: NavController,
    onTabViewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val backStackEntry by navController.currentBackStackEntryFlow.collectAsStateWithLifecycle(null)

    Surface(
        modifier = modifier
    ) {

    }
}