package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.digest.*
import org.slf4j.LoggerFactory
import java.nio.file.Path

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:

private val logger = LoggerFactory.getLogger("AgoraBot")

/**
 * @param channelMap a map of Discord channel ids to irc channels.
 */
private data class BaseCommandIrcOutputSink(
    private val channelMap: ImmutableMap<String, IrcChannel>,
) : BaseCommandOutputSink {
    constructor(channelMap: Map<String, IrcChannel>) : this(channelMap.toImmutableMap())

    private fun channelForEvent(event: MessageReceivedEvent): IrcChannel? {
        return channelMap[event.channel.id]
    }

    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        channelForEvent(event)?.run {
            message.lineSequence().forEach { sendMultiLineMessage(it) }
        }
    }

    override fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message) {
        channelForEvent(event)?.run {
            sendDiscordMessage(message)
        }
    }

    override fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        channelForEvent(event)?.run {
            val safeFileName = fileName.lineSequence().joinToString("") // Paranoia

            sendMultiLineMessage(
                "Well, I *would* send an attachment, and it *would* have been called \"$safeFileName\", " +
                        "but this is a lame forum that doesn't support attachments, so all you get is this message."
            )
        }
    }
}

private fun makeCommandRegistry(
    prefixMap: MutableGuildPrefixMap,
    digestMap: GuildDigestMap,
    digestFormat: DigestFormat,
    digestSendStrategy: DigestSendStrategy?,
): CommandRegistry {
    val strategy = DEFAULT_BASE_COMMAND_STRATEGY

    return MutableMapCommandRegistry(
        mapOf(
            "rng" to RngCommand(strategy),
            "digest" to DigestCommand(
                strategy = strategy,
                digestMap = digestMap,
                sendStrategy = digestSendStrategy,
                digestFormat = digestFormat,
            ),
            "copyright" to CopyrightCommand(strategy),
            "prefix" to PrefixCommand(strategy, prefixMap),
            "cfj" to CrystalBallCommand(strategy),
        ),
    ).also { it.addCommand("help", HelpCommand(strategy, it)) }
}

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()
    val persistService: ConfigPersistService = DefaultConfigPersistService

    val digestMap = JsonGuildDigestMap(Path.of(".", "digests"), persistService)

    val prefixMap = JsonPrefixMap(default = ".", Path.of(".", "prefixes"), persistService)
    val digestFormat = DefaultDigestFormat()

    val digestSendStrategy = readDigestSendStrategyConfig(Path.of(".", "mail.json"), digestFormat)
    if (digestSendStrategy == null) {
        logger.warn("Unable to setup digest sending! Check for errors above.")
    }

    val jda =
        JDABuilder
            .createDefault(
                token,
                listOf(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS,
                ),
            )
            .setEventManager(AnnotatedEventManager())
            .addEventListeners(
                BotListener(
                    MentionPrefixCommandParser(GuildPrefixCommandParser(prefixMap)),
                    makeCommandRegistry(prefixMap, digestMap, digestFormat, digestSendStrategy),
                ),
                digestEmoteListener(digestMap, DIGEST_ADD_EMOTE),
            )
            .build()

    jda.awaitReady()

    val ircDir = Path.of(".", "irc")
    val ircConfig = readIrcConfig(ircDir.resolve("config.json"))

    if (ircConfig == null) {
        logger.warn("Unable to setup IRC! Check for errors above.")
    } else {
        logger.info("Connecting IRC...")
        setupIrc(ircConfig, ircDir, jda)
        logger.info("Done connecting IRC.")
    }
}
