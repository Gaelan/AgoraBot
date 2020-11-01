package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.commands.impl.BaseCommandDiscordOutputSink.sendResponse
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.UserPermissionContext
import org.randomcat.agorabot.util.disallowMentions

interface BaseCommandArgumentStrategy {
    fun sendArgumentErrorResponse(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    )
}

interface BaseCommandOutputSink {
    fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String)
    fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message)

    fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    )
}

interface BaseCommandPermissionsStrategy {
    fun onPermissionsError(event: MessageReceivedEvent, invocation: CommandInvocation, permission: BotPermission)
    val permissionContext: BotPermissionContext
}

interface BaseCommandStrategy : BaseCommandArgumentStrategy, BaseCommandOutputSink, BaseCommandPermissionsStrategy

abstract class BaseCommand(private val strategy: BaseCommandStrategy) : Command {
    @CommandDslMarker
    class ExecutionReceiverImpl(
        private val strategy: BaseCommandStrategy,
        private val event: MessageReceivedEvent,
        private val invocation: CommandInvocation,
    ) {
        fun respond(message: String) {
            strategy.sendResponse(event, invocation, message)
        }

        fun respond(message: Message) {
            strategy.sendResponseMessage(event, invocation, message)
        }

        fun respondWithFile(fileName: String, fileContent: String) {
            strategy.sendResponseAsFile(event, invocation, fileName, fileContent)
        }

        fun currentMessageEvent() = event
        fun currentJda() = event.jda
        fun currentChannel() = currentMessageEvent().channel
        fun currentGuildId(): String = currentMessageEvent().guild.id
    }

    override fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation) {
        TopLevelPermissionsArgumentDescriptionReceiver(
            baseReceiver = TopLevelExecutingArgumentDescriptionReceiver<ExecutionReceiverImpl>(
                UnparsedCommandArgs(invocation.args),
                onError = { msg ->
                    strategy.sendArgumentErrorResponse(
                        event = event,
                        invocation = invocation,
                        errorMessage = msg,
                        usage = usage()
                    )
                },
                ExecutionReceiverImpl(strategy, event, invocation),
            ),
            data = PermissionsReceiverData.AllowExecution(
                userContext = event.member?.let { UserPermissionContext.InGuild(it) }
                    ?: UserPermissionContext.Guildless(event.author),
                permissionsContext = strategy.permissionContext,
                onError = { strategy.onPermissionsError(event, invocation, it) }
            )
        ).impl()
    }

    protected abstract fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl, PermissionsExtensionMarker>.impl()

    fun usage(): String {
        return TopLevelPermissionsArgumentDescriptionReceiver(
            baseReceiver = UsageTopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>(),
            data = PermissionsReceiverData.NeverExecute,
        ).apply { impl() }.base().usage()
    }
}

typealias BaseCommandImplReceiver =
        TopLevelArgumentDescriptionReceiver<BaseCommand.ExecutionReceiverImpl, PermissionsExtensionMarker>

object BaseCommandDiscordOutputSink : BaseCommandOutputSink {
    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        event.channel.sendMessage(message).disallowMentions().queue()
    }

    override fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message) {
        event.channel.sendMessage(message).disallowMentions().queue()
    }

    override fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        val bytes = fileContent.toByteArray(Charsets.UTF_8)
        event.channel.sendFile(bytes, fileName).disallowMentions().queue()
    }
}

object BaseCommandDefaultArgumentStrategy : BaseCommandArgumentStrategy {
    override fun sendArgumentErrorResponse(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    ) {
        sendResponse(event, invocation, "$errorMessage. Usage: ${usage.ifBlank { NO_ARGUMENTS }}")
    }
}

data class BaseCommandMultiOutputSink(
    private val outputs: ImmutableList<BaseCommandOutputSink>,
) : BaseCommandOutputSink {
    constructor(outputs: List<BaseCommandOutputSink>) : this(outputs.toImmutableList())

    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        outputs.forEach {
            it.sendResponse(
                event = event,
                invocation = invocation,
                message = message,
            )
        }
    }

    override fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message) {
        outputs.forEach {
            it.sendResponseMessage(
                event = event,
                invocation = invocation,
                message = message,
            )
        }
    }

    override fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        outputs.forEach {
            it.sendResponseAsFile(
                event = event,
                invocation = invocation,
                fileName = fileName,
                fileContent = fileContent,
            )
        }
    }
}
