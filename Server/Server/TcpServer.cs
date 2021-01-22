using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;

namespace Server
{
    public class TcpServer : IServer
    {
        private static string Date => DateTime.Now.ToString(CultureInfo.InvariantCulture);

        public bool Listening { get; private set; }

        private readonly TcpListener _listener;
        private CancellationTokenSource _tokenSource;
        private CancellationToken _token;

        private readonly ConcurrentDictionary<string, Client> _connectedClients =
            new ConcurrentDictionary<string, Client>();
        
        private readonly List<Task> _connections = new List<Task>();

        public TcpServer(IPAddress address, int port)
        {
            _listener = new TcpListener(address, port);
        }
        public async Task StartAsync(CancellationToken? token = null)
        {
            _tokenSource = CancellationTokenSource.CreateLinkedTokenSource(token ?? new CancellationToken());
            _token = _tokenSource.Token;
            _listener.Start();
            Listening = true;

            try
            {
                while (!_token.IsCancellationRequested)
                {
                    try
                    {
                        await Task.Yield();

                        var client = await _listener.AcceptTcpClientAsync();
                        
                        _connections.RemoveAll(task => task.IsCompleted);
                        
                        _connections.Add(Task.Run(() => SetUpConnection(client.GetStream()), _token));
                    }
                    catch (SocketException)
                    {
                        Console.WriteLine($"{Date}: Client disconnected before fully connecting");
                    }
                }

                Console.WriteLine($"{Date}: Loop ending");
            }
            catch (Exception e)
            {
                Console.WriteLine($"{Date}: Exception in client accept thread");
                Console.WriteLine(e);
            }
            finally
            {
                _listener.Stop();
                Listening = false;
            }
        }

        public void Stop()
        {
            _tokenSource?.Cancel();
        }
        
        public async ValueTask DisposeAsync()
        {
            Stop();
            await Task.WhenAll(_connections);
        }

        private async void SetUpConnection(Stream stream)
        {
            var client = new Client(stream, _connectedClients, _token);
            _connectedClients.TryAdd(client.Name, client);
            Console.WriteLine($"{Date}: Client connected: {client.Name}");
            try
            {
                var tasks = client.Start();
                await Task.WhenAny(tasks);
                client.Stop();
                await Task.WhenAll(tasks);
            }
            catch (TaskCanceledException)
            {
                Console.WriteLine($"{Date}: Client tasks cancelled");
            }
            Console.WriteLine($"{Date}: Removing client: {client.Name}");
            _connectedClients.TryRemove(client.Name, out _);
        }
    }
}