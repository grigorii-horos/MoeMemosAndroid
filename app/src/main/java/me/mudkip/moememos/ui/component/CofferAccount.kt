package me.mudkip.moememos.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.viewmodel.LocalUserState

/** True on a Coffer account, which has no archive and no "protected" visibility. */
@Composable
fun isCofferAccount(): Boolean {
    val account by LocalUserState.current.currentAccount.collectAsStateWithLifecycle()
    return account is Account.Coffer
}
