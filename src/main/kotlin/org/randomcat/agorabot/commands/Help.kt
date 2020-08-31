package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.Command
import org.randomcat.agorabot.QueryableCommandRegistry

private fun MessageBuilder.appendUsage(name: String, command: Command) {
    val usageHelp =
        if (command is BaseCommand)
            command.usage().ifBlank { NO_ARGUMENTS }
        else
            "<no usage available>"

    append(name, MessageBuilder.Formatting.BOLD)
    append(": $usageHelp")
}

class HelpCommand(private val registry: QueryableCommandRegistry) : ChatCommand() {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        matchFirst {
            noArgs {
                val commands = registry.commands()
                val builder = MessageBuilder()

                for ((name, command) in commands) {
                    builder.appendUsage(name = name, command = command)
                    builder.appendLine()
                }

                if (!builder.isEmpty) {
                    builder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { respond(it) }
                }
            }

            args(StringArg("command")) { args ->
                val commandName = args.first
                val commands = registry.commands()

                if (commands.containsKey(commandName)) {
                    val command = commands.getValue(commandName)

                    val builder = MessageBuilder()
                    builder.appendUsage(name = commandName, command = command)
                    builder.appendLine()

                    respond(builder.build())
                } else {
                    respond("No such command \"$commandName\".")
                }
            }
        }
    }
}