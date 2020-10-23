package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private const val END_DUCK_SESSION_CMD = "I fixed it"

private val RESPONSES = listOf(
    "Tell me more.",
    "Okay.",
    "How so?",
    "Nice.",
    "Good.",
    "Really?",
)

class DuckCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    private data class ListeningSpec(
        private val channelId: String,
        private val userId: String,
    )


    object JdaEventListenerMarker

    private val listening = AtomicReference<PersistentSet<ListeningSpec>>(persistentSetOf())
    private val jdaMap = Collections.synchronizedMap(WeakHashMap<JDA, JdaEventListenerMarker>())

    private fun registerJdaListener(jda: JDA) {
        if (!jdaMap.containsKey(jda)) {
            jda.addEventListener(object {
                @SubscribeEvent
                fun onMessage(event: GuildMessageReceivedEvent) {
                    if (!event.message.isFromGuild) return

                    val currentListening = listening.get()

                    val listeningSpec = ListeningSpec(
                        channelId = event.channel.id,
                        userId = event.author.id,
                    )

                    if (currentListening.contains(listeningSpec)) {
                        if (event.message.contentStripped.equals(END_DUCK_SESSION_CMD, ignoreCase = true)) {
                            event.channel.sendMessage("Good for you! I'll be quiet now.").queue()
                            jda.removeEventListener(this)
                        } else {
                            event.channel.sendMessage(RESPONSES.random()).queue()
                        }
                    }
                }
            })

            jdaMap.put(jda, JdaEventListenerMarker)
        }
    }

    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        subcommands {
            subcommand("start") {
                noArgs {
                    registerJdaListener(currentJda())

                    listening.updateAndGet {
                        it.add(ListeningSpec(
                            channelId = currentChannel().id,
                            userId = currentMessageEvent().author.id,
                        ))
                    }

                    respond("Alright, I'm listening. Type \"I fixed it\" to stop.")
                }
            }
        }
    }
}
