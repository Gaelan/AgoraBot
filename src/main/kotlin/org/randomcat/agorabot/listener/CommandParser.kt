package org.randomcat.agorabot.listener

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.util.splitArguments

data class CommandInvocation(val command: String, val args: ImmutableList<String>) {
    constructor(command: String, args: List<String>) : this(command, args.toImmutableList())
}

sealed class CommandParseResult {
    data class Invocation(val invocation: CommandInvocation) : CommandParseResult()
    data class Message(val message: String) : CommandParseResult()
    object Ignore : CommandParseResult()
}

interface CommandParser {
    fun parse(event: MessageReceivedEvent): CommandParseResult
}

/**
 * Parses a command as if by splitArguments, after removing [prefix]. The first argument is the command name, the rest
 * are the actual arguments. If the prefix is not present, or if there is no command after the prefix, returns Ignore.
 *
 * Throws [IllegalArgumentException] if [prefix] is empty.
 */
fun parsePrefixCommand(prefix: String, message: String): CommandParseResult {
    require(prefix.isNotEmpty())

    val payload = message.removePrefix(prefix)

    // If the prefix was not there to remove (when payload == message), there is no prefix, so no command.
    if (payload == message) return CommandParseResult.Ignore

    val parts = splitArguments(payload)
    if (parts.isEmpty()) return CommandParseResult.Ignore // Just a prefix, for some reason

    return CommandParseResult.Invocation(CommandInvocation(parts.first(), parts.drop(1)))
}

fun parsePrefixListCommand(prefixOptions: Iterable<String>, message: String): CommandParseResult {
    for (prefixOption in prefixOptions) {
        require(prefixOption.isNotEmpty())

        val parsed = parsePrefixCommand(prefixOption, message)
        if (parsed !is CommandParseResult.Ignore) return parsed
    }

    return CommandParseResult.Ignore
}

interface GuildPrefixMap {
    fun prefixForGuild(guildId: String): String
}

interface MutableGuildPrefixMap : GuildPrefixMap {
    fun setPrefixForGuild(guildId: String, prefix: String)
}

class GlobalPrefixCommandParser(private val prefix: String) : CommandParser {
    override fun parse(event: MessageReceivedEvent): CommandParseResult = parsePrefixCommand(
        prefix = prefix,
        message = event.message.contentRaw
    )
}

class GuildPrefixCommandParser(private val map: GuildPrefixMap) : CommandParser {
    override fun parse(event: MessageReceivedEvent): CommandParseResult = parsePrefixCommand(
        prefix = map.prefixForGuild(event.guild.id),
        message = event.message.contentRaw,
    )
}

class MentionPrefixCommandParser(private val fallback: CommandParser) : CommandParser {
    override fun parse(event: MessageReceivedEvent): CommandParseResult {
        val selfUserId = event.jda.selfUser.id
        val selfRoleId = event.guild.selfMember.roles.singleOrNull { it.isManaged }?.id

        // These are the two options for raw mentions; see https://discord.com/developers/docs/reference
        val mentionOptions = listOfNotNull("<@$selfUserId>", "<@!$selfUserId>", selfRoleId?.let { "<@&$it>" })

        val parseResult = parsePrefixListCommand(prefixOptions = mentionOptions, message = event.message.contentRaw)
        if (parseResult !is CommandParseResult.Ignore) return parseResult

        return fallback.parse(event)
    }
}
