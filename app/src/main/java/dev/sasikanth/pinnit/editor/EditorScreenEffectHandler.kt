package dev.sasikanth.pinnit.editor

import com.spotify.mobius.Connectable
import com.spotify.mobius.coroutines.MobiusCoroutines
import com.spotify.mobius.functions.Consumer
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dev.sasikanth.pinnit.notifications.NotificationRepository
import dev.sasikanth.pinnit.scheduler.PinnitNotificationScheduler
import dev.sasikanth.pinnit.utils.DispatcherProvider
import dev.sasikanth.pinnit.utils.notification.NotificationUtil
import kotlinx.coroutines.withContext

class EditorScreenEffectHandler @AssistedInject constructor(
  private val notificationRepository: NotificationRepository,
  private val dispatcherProvider: DispatcherProvider,
  private val notificationUtil: NotificationUtil,
  private val pinnitNotificationScheduler: PinnitNotificationScheduler,
  private val scheduleValidator: ScheduleValidator,
  @Assisted private val viewEffectConsumer: Consumer<EditorScreenViewEffect>
) {

  @AssistedFactory
  interface Factory {
    fun create(viewEffectConsumer: Consumer<EditorScreenViewEffect>): EditorScreenEffectHandler
  }

  fun build(): Connectable<EditorScreenEffect, EditorScreenEvent> {
    return MobiusCoroutines.effectHandler(dispatcherProvider.main) { effect, eventConsumer ->
      when (effect) {
        is LoadNotification -> loadNotification(effect, eventConsumer::accept)

        is SetTitleAndContent -> setTitleAndContent(effect)

        is SaveNotification -> saveNotification(effect, eventConsumer::accept)

        is UpdateNotification -> updateNotification(effect, eventConsumer::accept)

        ShowConfirmExitEditor -> showConfirmExitDialog()

        CloseEditor -> closeEditorView()

        ShowConfirmDelete -> showConfirmDeleteDialog()

        is DeleteNotification -> deleteNotification(effect)

        is ShowDatePicker -> showDatePicker(effect)

        is ShowTimePicker -> showTimePicker(effect)

        is ShowNotification -> showNotification(effect)

        is ScheduleNotification -> scheduleNotification(effect)

        is CancelNotificationSchedule -> cancelNotificationSchedule(effect)

        is ValidateSchedule -> validateSchedule(effect, eventConsumer::accept)
      }
    }
  }

  private suspend fun showTimePicker(effect: ShowTimePicker) {
    withContext(dispatcherProvider.main) {
      viewEffectConsumer.accept(ShowTimePickerDialog(effect.time))
    }
  }

  private suspend fun showDatePicker(effect: ShowDatePicker) {
    withContext(dispatcherProvider.main) {
      viewEffectConsumer.accept(ShowDatePickerDialog(effect.date))
    }
  }

  private suspend fun showConfirmDeleteDialog() {
    withContext(dispatcherProvider.main) {
      viewEffectConsumer.accept(ShowConfirmDeleteDialog)
    }
  }

  private suspend fun showConfirmExitDialog() {
    withContext(dispatcherProvider.main) {
      viewEffectConsumer.accept(ShowConfirmExitEditorDialog)
    }
  }

  private suspend fun loadNotification(
    effect: LoadNotification,
    dispatchEvent: (EditorScreenEvent) -> Unit
  ) {
    withContext(dispatcherProvider.io) {
      val notification = notificationRepository.notification(effect.uuid)
      dispatchEvent(NotificationLoaded(notification))
    }
  }

  private suspend fun setTitleAndContent(effect: SetTitleAndContent) {
    withContext(dispatcherProvider.main) {
      viewEffectConsumer.accept(SetTitle(effect.title))
      viewEffectConsumer.accept(SetContent(effect.content))
    }
  }

  private suspend fun saveNotification(effect: SaveNotification, dispatchEvent: (EditorScreenEvent) -> Unit) {
    withContext(dispatcherProvider.io) {
      val notification = notificationRepository.save(
        title = effect.title,
        content = effect.content,
        isPinned = effect.canPinNotification,
        schedule = effect.schedule
      )
      dispatchEvent(NotificationSaved(notification))
    }
  }

  private suspend fun updateNotification(effect: UpdateNotification, dispatchEvent: (EditorScreenEvent) -> Unit) {
    withContext(dispatcherProvider.io) {
      val notification = notificationRepository.notification(effect.notificationUuid)
      val updatedNotification = notificationRepository.updateNotification(
        notification.copy(
          title = effect.title,
          content = effect.content,
          schedule = effect.schedule
        )
      )

      dispatchEvent(NotificationUpdated(updatedNotification))
    }
  }

  private suspend fun deleteNotification(
    effect: DeleteNotification
  ) {
    withContext(dispatcherProvider.io) {
      val notification = effect.notification
      notificationRepository.updatePinStatus(notification.uuid, false)
      notificationRepository.deleteNotification(notification)
      notificationUtil.dismissNotification(notification)
      pinnitNotificationScheduler.cancel(notification.uuid)
      closeEditorView()
    }
  }

  private suspend fun closeEditorView() {
    withContext(dispatcherProvider.main) {
      viewEffectConsumer.accept(CloseEditorView)
    }
  }

  private suspend fun showNotification(effect: ShowNotification) {
    withContext(dispatcherProvider.default) {
      notificationUtil.showNotification(effect.notification)
    }
  }

  private suspend fun scheduleNotification(effect: ScheduleNotification) {
    withContext(dispatcherProvider.default) {
      pinnitNotificationScheduler.scheduleNotification(effect.notification)
    }
  }

  private suspend fun cancelNotificationSchedule(effect: CancelNotificationSchedule) {
    withContext(dispatcherProvider.default) {
      pinnitNotificationScheduler.cancel(effect.notificationId)
    }
  }

  private suspend fun validateSchedule(effect: ValidateSchedule, dispatchEvent: (EditorScreenEvent) -> Unit) {
    withContext(dispatcherProvider.default) {
      val result = scheduleValidator.validate(effect.scheduleDate, effect.scheduleTime)
      dispatchEvent(ScheduleValidated(result))
    }
  }
}
