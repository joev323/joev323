package org.infobip.mobile.messaging.storage;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Base64;

import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.util.PreferenceHelper;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores messages in shared preferences.
 * <pre>
 * {@code
 * public class MyActivity extends AppCompatActivity {
 *        protected void onCreate(Bundle savedInstanceState) {
 *            super.onCreate(savedInstanceState);
 *
 *            new MobileMessaging.Builder(this)
 *                .withMessageStore(SharedPreferencesMessageStore.class);
 *                .build();
 *        }
 *    }}
 * </pre>
 *
 * Overwrite SharedPreferencesMessageStore#getStoreTag(String)
 * to provide alternate key to store data shared preferences.
 *
 * @author mstipanov
 * @since 29.03.2016.
 */
public class SharedPreferencesMessageStore implements MessageStore {

    private static final String INFOBIP_MESSAGE_DATA_SET = "org.infobip.mobile.messaging.store.DATA";

    public void save(Context context, Message... messages) {
        addMessages(context, messages);
    }

    public List<Message> findAll(Context context) {
        return PreferenceHelper.find(context, getStoreTag(), new ArrayList<Message>(), new PreferenceHelper.SetConverter<List<Message>>() {
            @Override
            protected List<Message> convert(Set<String> set) {
                List<Message> messages = new ArrayList<Message>();
                for (String bundle : set) {
                    messages.add(Message.createFrom(deserialize(bundle)));
                }
                return messages;
            }
        });
    }

    public void deleteAll(Context context) {
        PreferenceHelper.remove(context, getStoreTag());
    }

    public long countAll(Context context) {
        return findAll(context).size();
    }

    public List<Message> bind(final Context context) {
        return new AbstractList<Message>() {
            @Override
            public Message get(int location) {
                return findAll(context).get(location);
            }

            @Override
            public int size() {
                return findAll(context).size();
            }
        };
    }

    private void addMessages(final Context context, final Message... messages) {
        PreferenceHelper.editSet(context, getStoreTag(), new PreferenceHelper.SetMutator() {
            @Override
            protected void mutate(Set<String> set) {
                for (Message message : messages) {
                    for (String serializedBundle : new HashSet<>(set)) {
                        Message messageInSet = Message.createFrom(deserialize(serializedBundle));
                        if (messageInSet.getMessageId().equals(message.getMessageId())) {
                            set.remove(serializedBundle);
                            break;
                        }
                    }

                    set.add(serialize(message.getBundle()));
                }
            }
        });

        MobileMessagingCore.getInstance(context).activateGeofencing();
    }

    private String serialize(Bundle in) {
        Parcel parcel = Parcel.obtain();
        try {
            in.writeToParcel(parcel, 0);
            return Base64.encodeToString(parcel.marshall(), 0);
        } finally {
            parcel.recycle();
        }
    }

    private Bundle deserialize(String serialized) {
        if (serialized == null) {
            return null;
        }

        Parcel parcel = Parcel.obtain();
        try {
            byte[] data = Base64.decode(serialized, 0);
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            return parcel.readBundle();
        } finally {
            parcel.recycle();
        }
    }

    protected String getStoreTag() {
        return INFOBIP_MESSAGE_DATA_SET;
    }
}
