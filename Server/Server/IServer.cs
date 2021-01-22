using System;
using System.Threading;
using System.Threading.Tasks;

namespace Server
{
    public interface IServer : IAsyncDisposable
    {
        bool Listening { get; }

        Task StartAsync(CancellationToken? token = null);
        
        void Stop();
    }
}