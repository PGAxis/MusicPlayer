using Microsoft.Maui;
#if ANDROID
    using MusicPlayer.Platforms.Android;
#endif

namespace MusicPlayer
{
    public partial class MainPage : ContentPage
    {
        private bool CanScroll = true;
        private bool JustResized = false;
        private Dictionary<Label, ContentView> Tabs = new Dictionary<Label, ContentView>();
        private Label LastTab;
        private List<IResizablePage> Pages = new List<IResizablePage>();

        private Favourites Favourites = Favourites.Instance();
        private Playlists Playlists = Playlists.Instance();
        private Songs Songs = Songs.Instance();
        private Albums Albums = Albums.Instance();
        private Interprets Interprets = Interprets.Instance();

        public MainPage()
        {
            InitializeComponent();
            SetCorrectWidthRequest();
            LastTab = FavLabel;
            LastTab.FontAttributes = FontAttributes.Bold;
            SetCorrectFontSize();

            Tabs.Add(FavLabel, FavouritesView);
            Tabs.Add(PlayLabel, PlaylistsView);
            Tabs.Add(SongLabel, SongsView);
            Tabs.Add(AlbLabel, AlbumsView);
            Tabs.Add(IntLabel, InterpretsView);

            Pages.Add(Favourites);
            Pages.Add(Playlists);
            Pages.Add(Songs);
            Pages.Add(Albums);
            Pages.Add(Interprets);

            FavouritesView.Content = Favourites.Content;
            PlaylistsView.Content = Playlists.Content;
            SongsView.Content = Songs.Content;
            AlbumsView.Content = Albums.Content;
            InterpretsView.Content = Interprets.Content;

#if ANDROID
            NotificationHelper.CreateNotificationChannel(Android.App.Application.Context);
            NotificationHelper.ShowNotification(Android.App.Application.Context, "Hello", "TestNotification");
#endif
        }

        private void UserScrolled(object sender, ScrolledEventArgs e)
        {
            double curScrollX = e.ScrollX;
            double ViewScrollX = curScrollX * 2;

            double screenCenter = TabScroll.Width / 2;

            foreach (var child in LabelStack.Children)
            {
                if (child is Label label)
                {
                    double labelCenter = label.X + (label.Width / 2) - TabScroll.ScrollX;
                    double distance = Math.Abs(screenCenter - labelCenter);

                    double maxFont = 30;
                    double minFont = 20;
                    double maxDistance = TabScroll.Width / 2;

                    double factor = 1 - Math.Min(distance / maxDistance, 1);
                    label.FontSize = minFont + (maxFont - minFont) * factor;
                }
            }
            if (CanScroll)
            {
                //_ = ViewScroll.ScrollToAsync(ViewScrollX, 0, false);

                _ = CheckScrollChange(curScrollX, TabScroll);
            }
        }

        private void ViewScrolled(object sender, ScrolledEventArgs e)
        {
            double curScrollX = e.ScrollX;
            double TabScrollX = curScrollX * 0.5;
            if (CanScroll)
            {
                _ = TabScroll.ScrollToAsync(TabScrollX, 0, false);

                _ = CheckScrollChange(curScrollX, ViewScroll);
            }
        }

        //Help methods
        private void SetCorrectWidthRequest()
        {
            double ViewHeightRequest = (DeviceDisplay.MainDisplayInfo.Height / DeviceDisplay.MainDisplayInfo.Density) - 160;
            double ViewRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
            double LabelRequest = ViewRequest * 0.5;
            double PaddingRequest = (ViewRequest - LabelRequest) / 2;

            LeftPadding.WidthRequest = PaddingRequest;
            RightPadding.WidthRequest = PaddingRequest;

            FavLabel.WidthRequest = LabelRequest;
            PlayLabel.WidthRequest = LabelRequest;
            SongLabel.WidthRequest = LabelRequest;
            AlbLabel.WidthRequest = LabelRequest;
            IntLabel.WidthRequest = LabelRequest;

            foreach (var child in ViewStack.Children)
            {
                if (child is ContentView contentView)
                {
                    contentView.WidthRequest = ViewRequest;
                    contentView.HeightRequest = ViewHeightRequest;
                }
            }
        }

        private async Task CheckScrollChange(double scrollX, ScrollView sender)
        {
            await Task.Delay(100);

            double curX = sender.ScrollX;

            bool hasntChanged = AreCloseEnough(curX, scrollX);

            if (hasntChanged && CanScroll)
            {
                if (JustResized)
                {
                    await Task.Delay(100);
                    JustResized = false;
                }
                Label closest = GetClosestLabel();
                CanScroll = false;
                await TabScroll.ScrollToAsync(closest, ScrollToPosition.Center, false);
                await ViewScroll.ScrollToAsync(Tabs[closest], ScrollToPosition.Center, false);
                LastTab.FontAttributes = FontAttributes.None;
                LastTab = closest;
                LastTab.FontAttributes = FontAttributes.Bold;
                CanScroll = true;
            }
        }

        private bool AreCloseEnough(double a, double b)
        {
            return Math.Abs(a - b) == 0.0;
        }

        /*private ContentView GetClosestView()
        {
            double scrollX = ViewScroll.ScrollX;
            double scrollWidth = ViewScroll.Width;
            double viewportCenter = scrollX + scrollWidth / 2;

            var stack = (HorizontalStackLayout)ViewScroll.Content;

            ContentView closest = null;
            double smallestDistance = double.MaxValue;

            double runningX = 0;

            foreach (var child in stack.Children)
            {
                double childCenter = runningX + child.Width / 2;

                double distance = Math.Abs(viewportCenter - childCenter);
                if (distance < smallestDistance)
                {
                    smallestDistance = distance;
                    closest = (ContentView)child;
                }

                runningX += child.Width;
            }

            return closest;
        }*/

        private Label GetClosestLabel()
        {
            double scrollX = TabScroll.ScrollX;
            double scrollWidth = TabScroll.Width;
            double viewportCenter = scrollX + scrollWidth / 2;

            HorizontalStackLayout stack = LabelStack;

            Label closest = null;
            double smallestDistance = double.MaxValue;

            double runningX = 0;

            foreach (var child in stack.Children)
            {
                double childCenter = runningX + child.Width / 2;

                double distance = Math.Abs(viewportCenter - childCenter);
                if (distance < smallestDistance)
                {
                    if (child is Label label)
                    {
                        smallestDistance = distance;
                        closest = (Label)child;
                    }
                }

                runningX += child.Width;
            }

            return closest;
        }

        private void SetCorrectFontSize()
        {
            foreach (var child in LabelStack.Children)
            {
                if (child is Label label)
                {
                    if (child != LastTab)
                    {
                        label.FontSize = 20;
                    }
                }
            }
        }
    }
}
