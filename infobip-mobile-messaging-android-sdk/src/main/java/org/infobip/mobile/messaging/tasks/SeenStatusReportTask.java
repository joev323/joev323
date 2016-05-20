package org.infobip.mobile.messaging.tasks;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.infobip.mobile.messaging.Event;
import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.MobileMessaging;
import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.api.seenstatus.SeenMessages;

import java.util.List;

import static org.infobip.mobile.messaging.BroadcastParameter.EXTRA_PARAMETER_EXCEPTION;

/**
 * @author sslavin
 * @since 25.04.2016.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class SeenStatusReportTask extends AsyncTask<Object, Void, SeenStatusReportResult> {
    private final Context context;

    public SeenStatusReportTask(Context context) {
        this.context = context;
    }

    @Override
    protected SeenStatusReportResult doInBackground(Object... notUsed) {
        MobileMessagingCore mobileMessagingCore = MobileMessagingCore.getInstance(context);
        try {
            String messageIDs[] = mobileMessagingCore.getUnreportedSeenMessageIds();
            SeenMessages seenMessages;
            if (mobileMessagingCore.isMessageStoreEnabled()) {
                List<Message> messages = mobileMessagingCore.getMessageStore().findAllMatching(context, messageIDs);
                seenMessages = SeenMessagesReport.fromMessages(messages);
            } else {
                seenMessages = SeenMessagesReport.fromMessageIds(messageIDs);
            }
            MobileApiResourceProvider.INSTANCE.getMobileApiSeenStatusReport(context).report(seenMessages);
            mobileMessagingCore.removeUnreportedSeenMessageIds(messageIDs);
            return new SeenStatusReportResult(messageIDs);
        } catch (Exception e) {
            mobileMessagingCore.setLastHttpException(e);
            Log.e(MobileMessaging.TAG, "Error reporting seen status!", e);
            cancel(true);

            Intent seenStatusReportError = new Intent(Event.API_COMMUNICATION_ERROR.getKey());
            seenStatusReportError.putExtra(EXTRA_PARAMETER_EXCEPTION, e);
            context.sendBroadcast(seenStatusReportError);
            LocalBroadcastManager.getInstance(context).sendBroadcast(seenStatusReportError);

            return null;
        }
    }
}
