using Android.App;
using Android.Content;
using Android.Widget;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    [BroadcastReceiver(Enabled = true, Exported = true)]
    [IntentFilter(new[] { "SHUFFLE", "PLAY", "PAUSE", "NEXT", "PREVIOUS", "REPEAT" })]
    public class NotificationActionReceiver : BroadcastReceiver
    {
        public override void OnReceive(Context context, Intent intent)
        {
            var action = intent.Action;

            Microsoft.Maui.Controls.MessagingCenter.Send<object, string>(this, "MediaAction", action);

            Toast.MakeText(context, $"Action: {action}", ToastLength.Short).Show();
        }
    }
}
