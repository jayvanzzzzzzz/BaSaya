package ph.basaya.app.data.auth

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import org.json.JSONArray
import org.json.JSONObject

/** Local profile hints only. Every selected account still authenticates with Firebase. */
data class RecentAccount(val uid: String, val email: String, val name: String, val provider: String)

object RecentAccounts {
    private const val PREFS = "recent_accounts"

    fun list(context: Context): List<RecentAccount> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("accounts", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val uid = item.optString("uid").trim().take(160)
            val email = item.optString("email").trim().take(254)
            if (uid.isBlank() || email.isBlank()) return@mapNotNull null
            RecentAccount(uid, email, item.optString("name").trim().take(160).ifBlank { email.substringBefore('@') },
                if (item.optString("provider") == "google.com") "google.com" else "password")
        }.distinctBy { it.uid }.take(5)
    }.getOrDefault(emptyList())

    fun remember(context: Context, user: FirebaseUser?, profile: Map<String, Any?> = emptyMap()) {
        if (user == null || user.email.isNullOrBlank()) return
        val fullName = listOf("firstName", "middleName", "lastName")
            .mapNotNull { (profile[it] as? String)?.trim()?.takeIf(String::isNotBlank) }.joinToString(" ")
        val name = (profile["displayName"] as? String)?.trim()?.takeIf(String::isNotBlank)
            ?: fullName.takeIf(String::isNotBlank) ?: user.displayName?.takeIf(String::isNotBlank)
            ?: list(context).firstOrNull { it.uid == user.uid }?.name ?: user.email!!.substringBefore('@')
        val provider = if (user.providerData.any { it.providerId == "password" }) "password"
            else if (user.providerData.any { it.providerId == "google.com" }) "google.com" else "password"
        val account = RecentAccount(user.uid, user.email!!, name.take(160), provider)
        write(context, (listOf(account) + list(context).filter { it.uid != user.uid }).take(5))
    }

    fun remove(context: Context, uid: String): Boolean = write(context, list(context).filter { it.uid != uid })

    private fun write(context: Context, accounts: List<RecentAccount>): Boolean = runCatching {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(JSONObject().put("uid", account.uid).put("email", account.email)
                .put("name", account.name).put("provider", account.provider))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("accounts", array.toString()).commit()
    }.getOrDefault(false)
}
