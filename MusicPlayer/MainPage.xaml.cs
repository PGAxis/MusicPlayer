using Microsoft.Maui;

namespace MusicPlayer
{
    public partial class MainPage : ContentPage
    {
        private bool CanScroll = true;
        private Label LastTab;

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
            FavouritesView.Content = Favourites.Content;
            PlaylistsView.Content = Playlists.Content;
            SongsView.Content = Songs.Content;
            AlbumsView.Content = Albums.Content;
            InterpretsView.Content = Interprets.Content;
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

            FavouritesView.WidthRequest = ViewRequest;
            PlaylistsView.WidthRequest = ViewRequest;
            SongsView.WidthRequest = ViewRequest;
            AlbumsView.WidthRequest = ViewRequest;
            InterpretsView.WidthRequest = ViewRequest;
        }

        private async Task CheckScrollChange(double scrollX, ScrollView sender)
        {
            await Task.Delay(100);

            double curX = sender.ScrollX;

            bool hasntChanged = AreCloseEnough(curX, scrollX);

            if (hasntChanged && CanScroll)
            {
                CanScroll = false;
                await TabScroll.ScrollToAsync(GetClosestLabel(), ScrollToPosition.Center, false);
                await ViewScroll.ScrollToAsync(TabScroll.ScrollX * 2, 0, false);
                LastTab.FontAttributes = FontAttributes.None;
                LastTab = GetClosestLabel();
                LastTab.FontAttributes = FontAttributes.Bold;
                CanScroll = true;
            }
        }

        private bool AreCloseEnough(double a, double b)
        {
            return Math.Abs(a - b) == 0.0;
        }

        private ContentView GetClosestView()
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
        }

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
