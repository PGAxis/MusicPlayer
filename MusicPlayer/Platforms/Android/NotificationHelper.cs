using Android.App;
using Android.Content;
using Android.OS;

namespace MusicPlayer.Platforms.Android
{
    public static class NotificationHelper
    {
        const string CHANNEL_ID = "media_playback_channel";

        public static void CreateNotificationChannel(Context context)
        {
            if (Build.VERSION.SdkInt >= BuildVersionCodes.O)
            {
                var channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback",
                    NotificationImportance.Default)
                {
                    Description = "Controls for audio playback"
                };

                var manager = (NotificationManager)context.GetSystemService(Context.NotificationService);
                manager.CreateNotificationChannel(channel);
            }
        }

        public static void ShowNotification(Context context, string title, string message)
        {
            var builder = new Notification.Builder(context, CHANNEL_ID)
                .SetContentTitle(title)
                .SetContentText(message)
                .SetSmallIcon(Resource.Drawable.splash)
                .SetAutoCancel(true);

            var notification = builder.Build();

            var manager = NotificationManager.FromContext(context);
            manager.Notify(1001, notification);
        }
    }
}
