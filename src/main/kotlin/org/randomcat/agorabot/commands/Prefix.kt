package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.listener.MutableGuildPrefixMap
import org.randomcat.agorabot.permissions.GuildScope

private val PROHIBITED_CATEGORIES = listOf(
    CharCategory.CONTROL,
    CharCategory.FORMAT,
    CharCategory.LINE_SEPARATOR,
    CharCategory.UNASSIGNED,
    CharCategory.NON_SPACING_MARK,
    CharCategory.PRIVATE_USE,
)

class PrefixCommand(
    strategy: BaseCommandStrategy,
    private val prefixMap: MutableGuildPrefixMap,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            noArgs {
                val guildId = currentGuildInfo()?.guildId ?: run {
                    respondNeedGuild()
                    return@noArgs
                }

                respond("The prefix is: ${prefixMap.prefixForGuild(guildId)}")
            }

            args(
                StringArg("new_prefix"),
            ).permissions(
                GuildScope.command("prefix").action("set"),
            ) { (newPrefix) ->
                val guildId = currentGuildInfo()?.guildId ?: run {
                    respondNeedGuild()
                    return@permissions
                }

                if (newPrefix.isBlank()) {
                    respond("The prefix cannot be empty. Stop it.")
                    return@permissions
                }

                if (newPrefix.any { PROHIBITED_CATEGORIES.contains(it.category) }) {
                    respond("The specified prefix contains an illegal character.")
                    return@permissions
                }

                prefixMap.setPrefixForGuild(guildId, newPrefix)
                respond("The prefix has been updated.")
            }
        }
    }
}
