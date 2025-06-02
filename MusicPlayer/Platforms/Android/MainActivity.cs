using Android;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using AndroidX.Core.App;
using System.Threading.Tasks;

namespace MusicPlayer
{
    [Activity(Theme = "@style/Maui.SplashTheme", MainLauncher = true, LaunchMode = LaunchMode.SingleTop, ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode | ConfigChanges.ScreenLayout | ConfigChanges.SmallestScreenSize | ConfigChanges.Density, ScreenOrientation = ScreenOrientation.Portrait)]
    public class MainActivity : MauiAppCompatActivity
    {
        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            // Change the status bar color to transparent
            if (Build.VERSION.SdkInt >= BuildVersionCodes.Lollipop)
            {
                Window.SetStatusBarColor(Android.Graphics.Color.Transparent);
            }

            RequestNotificationPermission();
        }

        async Task RequestNotificationPermission()
        {
            if (Build.VERSION.SdkInt >= BuildVersionCodes.Tiramisu)
            {
                if (CheckSelfPermission(Manifest.Permission.PostNotifications) != Permission.Granted)
                {
                    ActivityCompat.RequestPermissions(this, new[] { Manifest.Permission.PostNotifications }, 101);
                }
                if (CheckSelfPermission(Manifest.Permission.ReadMediaAudio) != Permission.Granted)
                {
                    ActivityCompat.RequestPermissions(this, new[] { Manifest.Permission.ReadMediaAudio }, 101);
                }
                if (CheckSelfPermission(Manifest.Permission.ReadExternalStorage) != Permission.Granted)
                {
                    ActivityCompat.RequestPermissions(this, new[] { Manifest.Permission.ReadExternalStorage }, 101);
                }
            }
        }
    }
}
