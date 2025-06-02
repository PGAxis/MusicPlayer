using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Linq;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    public class ObservableRangeCollection<T> : ObservableCollection<T>
    {
        private bool supressNotification;

        public void AddRange(IEnumerable<T> items)
        {
            if (items == null) return;

            supressNotification = true;

            foreach (var item in items)
                Items.Add(item);

            supressNotification = false;
            RaiseReset();
        }

        public void ReplaceRange(IEnumerable<T> items)
        {
            if (items == null) return;

            supressNotification = true;

            Items.Clear();
            foreach (var item in items)
                Items.Add(item);

            supressNotification = false;
            RaiseReset();
        }

        private void RaiseReset()
        {
            OnCollectionChanged(new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset));
            OnPropertyChanged(new PropertyChangedEventArgs(nameof(Count)));
            OnPropertyChanged(new PropertyChangedEventArgs("Item[]"));
        }

        protected override void OnCollectionChanged(NotifyCollectionChangedEventArgs e)
        {
            if (!supressNotification)
                base.OnCollectionChanged(e);
        }
    }
}
