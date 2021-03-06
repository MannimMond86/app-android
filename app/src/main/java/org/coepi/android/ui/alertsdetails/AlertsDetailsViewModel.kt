package org.coepi.android.ui.alertsdetails

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers.io
import io.reactivex.subjects.PublishSubject
import org.coepi.android.R.string.alerts_details_distance_avg
import org.coepi.android.R.string.alerts_details_duration_hours_minutes
import org.coepi.android.R.string.alerts_details_duration_minutes
import org.coepi.android.R.string.alerts_details_duration_seconds
import org.coepi.android.R.string.alerts_details_report_email_address
import org.coepi.android.R.string.alerts_details_report_email_subject
import org.coepi.android.R.string.alerts_details_reported_on
import org.coepi.android.extensions.rx.toLiveData
import org.coepi.android.repo.AlertsRepo
import org.coepi.android.system.Email
import org.coepi.android.system.Resources
import org.coepi.android.system.UnitsProvider
import org.coepi.android.system.log.log
import org.coepi.android.ui.extensions.ExposureDurationForUI
import org.coepi.android.ui.extensions.ExposureDurationForUI.HoursMinutes
import org.coepi.android.ui.extensions.ExposureDurationForUI.Minutes
import org.coepi.android.ui.extensions.ExposureDurationForUI.Seconds
import org.coepi.android.ui.extensions.durationForUI
import org.coepi.android.ui.extensions.symptomUIStrings
import org.coepi.android.ui.formatters.DateFormatters.hourMinuteFormatter
import org.coepi.android.ui.formatters.DateFormatters.monthDayFormatter
import org.coepi.android.ui.formatters.LengthFormatter
import org.coepi.android.ui.navigation.NavigationCommand.Back
import org.coepi.android.ui.navigation.RootNavigation
import org.coepi.core.domain.model.Alert
import org.coepi.core.domain.model.UnixTime
import org.coepi.core.domain.model.LengthtUnit

class AlertsDetailsViewModel(
    private val args: AlertsDetailsFragment.Args,
    private val resources: Resources,
    private val navigation: RootNavigation,
    private val alertsRepo: AlertsRepo,
    private val email: Email,
    private val nav: RootNavigation,
    unitsProvider: UnitsProvider,
    private val lengthFormatter: LengthFormatter
) : ViewModel() {
    val viewData: LiveData<AlertDetailsViewData> = unitsProvider.lengthUnit
        .map { args.alert.toViewData(it, args.linkedAlerts.isNotEmpty()) }
        .toLiveData()

    val linkedAlertsViewData: List<LinkedAlertViewData> = args.linkedAlerts
        .mapIndexed { index, alert -> alert.toLinkedAlertViewData(
            image = LinkedAlertViewDataConnectionImage.from(index, args.linkedAlerts.size),
            bottomLine = index < args.linkedAlerts.size - 1
        )}

    private val deleteAlertTrigger: PublishSubject<Unit> = PublishSubject.create()

    private val disposables = CompositeDisposable()

    init {
        disposables += deleteAlertTrigger
            .subscribeOn(io())
            .doOnNext { alertsRepo.removeAlert(args.alert) }
            .observeOn(mainThread())
            .subscribe { nav.navigate(Back) }
    }

    fun onBack() {
        navigation.navigate(Back)
    }

    private fun Alert.toViewData(unit: LengthtUnit,
                                 showOtherExposuresHeader: Boolean): AlertDetailsViewData =
        AlertDetailsViewData(
            title = formattedStartDate(),
            contactStart = formattedContactStart(),
            contactDuration = formattedContactDuration(),
            avgDistance = formattedAvgDistance(unit),
            minDistance = formattedMinDistance(unit), // Temporary, for testing
            reportTime = formatReportTime(),
            symptoms = formattedSymptoms(),
            showOtherExposuresHeader = showOtherExposuresHeader,
            alert = this
        ).also {
            log.d("Showing details for alert: $this")
        }

    private fun Alert.toLinkedAlertViewData(image: LinkedAlertViewDataConnectionImage,
                                            bottomLine: Boolean): LinkedAlertViewData =
        LinkedAlertViewData(
            date = formattedStartDate(),
            contactStart = formattedContactStart(),
            contactDuration = formattedContactDuration(),
            symptoms = formattedSymptoms(),
            alert = this,
            image = image,
            bottomLine = bottomLine
        )

    private fun Alert.formattedStartDate(): String =
        toMonthAndDay(contactStart)

    private fun Alert.formattedContactStart(): String =
        hourMinuteFormatter.formatTime(contactStart.toDate())

    private fun Alert.formattedContactDuration(): String =
        durationForUI.toLocalizedString()

    private fun Alert.formattedSymptoms(): String = symptomUIStrings(resources)
        .joinToString("\n")

    private fun Alert.formattedAvgDistance(unit: LengthtUnit): String =
        resources.getString(
            alerts_details_distance_avg,
            lengthFormatter.format(avgDistance.convert(unit))
        )

    private fun Alert.formattedMinDistance(unit: LengthtUnit): String =
        "[DEBUG] Min distance: ${lengthFormatter.format(minDistance.convert(unit))}"

    private fun Alert.formatReportTime(): String = reportTime.toDate().let { date ->
        resources.getString(
            alerts_details_reported_on,
            monthDayFormatter.formatMonthDay(date),
            hourMinuteFormatter.formatTime(date)
        )
    }

    private fun toMonthAndDay(time: UnixTime): String =
        monthDayFormatter.formatMonthDay(time.toDate())

    fun onDeleteTap() {
        deleteAlertTrigger.onNext(Unit)
    }

    fun onReportProblemTap(activity: Activity) {
        email.open(activity,
            resources.getString(alerts_details_report_email_subject),
            resources.getString(alerts_details_report_email_address)
        )
    }

    private fun ExposureDurationForUI.toLocalizedString(): String = when (this) {
        is HoursMinutes -> resources.getString(alerts_details_duration_hours_minutes, hours, minutes)
        is Minutes -> resources.getString(alerts_details_duration_minutes, value)
        is Seconds -> resources.getString(alerts_details_duration_seconds, value)
    }
}

val Alert.contactDuration: Long get() =
    contactEnd.value - contactStart.value
