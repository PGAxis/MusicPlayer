using Android.App;
using Android.Content;
using Android.OS;
using Android.Runtime;
using Android.Support.V4.Media.Session;

namespace MusicPlayer
{
    [Application]
    public class MainApplication : MauiApplication
    {
        public static MediaSessionCompat MediaSession {  get; private set; }

        public MainApplication(IntPtr handle, JniHandleOwnership ownership)
            : base(handle, ownership)
        {
        }

        public override void OnCreate()
        {
            base.OnCreate();

            var context = this.ApplicationContext;
            MediaSession = new MediaSessionCompat(context, "MyMediaSession");
            MediaSession.SetCallback(new SessionCallback());
            MediaSession.Active = true;
        }

        protected override MauiApp CreateMauiApp() => MauiProgram.CreateMauiApp();
    }

    public class SessionCallback : MediaSessionCompat.Callback
    {
        public override void OnPlay()
        {
            base.OnPlay();
        }

        public override void OnPause()
        {
            base.OnPause();
        }

        public override void OnSkipToNext()
        {
            base.OnSkipToNext();
        }

        public override void OnSkipToPrevious()
        {
            base.OnSkipToPrevious();
        }
    }
}
