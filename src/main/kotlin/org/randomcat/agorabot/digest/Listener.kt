package org.randomcat.agorabot.digest

import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import org.randomcat.agorabot.listener.BotEmoteListener

private const val DISCORD_WHITE_CHECK_MARK = "\u2705"

fun digestEmoteListener(digestMap: GuildDigestMap, targetEmoji: String): BotEmoteListener {
    val functor = object {
        operator fun invoke(event: MessageReactionAddEvent) {
            val emote = event.reactionEmote
            if (!emote.isEmoji) return

            val reactionEmoji = emote.emoji
            if (reactionEmoji == targetEmoji) {
                val digest = digestMap.digestForGuild(event.guild.id)

                event.retrieveMessage().queue { message ->
                    message.retrieveDigestMessage().queue { digestMessage ->
                        val numAdded = digest.addCounted(digestMessage)

                        if (numAdded > 0) {
                            message
                                .addReaction(DISCORD_WHITE_CHECK_MARK)
                                .mapToResult() // Ignores failure if no permission to react
                                .queue()
                        }
                    }
                }
            }
        }
    }

    return BotEmoteListener { functor(it) }
}
