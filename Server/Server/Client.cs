using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Threading.Tasks.Dataflow;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace Server
{
    public class Client : IClient
    {
        private static string Date => DateTime.Now.ToString(CultureInfo.InvariantCulture);
        public string Name { get; } = Guid.NewGuid().ToString();
        
        private readonly BufferBlock<string> _messagesToSend = new BufferBlock<string>();
        
        private readonly ConcurrentDictionary<string, byte> _sessions = new ConcurrentDictionary<string, byte>();

        private readonly CancellationTokenSource _tokenSource;
        private readonly CancellationToken _token;
        
        private readonly ConcurrentDictionary<string, Client> _connectedClients;

        private readonly Stream _stream;

        public Client(Stream stream, ConcurrentDictionary<string, Client> connectedClients, CancellationToken token)
        {
            _stream = stream;
            _connectedClients = connectedClients;
            _tokenSource = CancellationTokenSource.CreateLinkedTokenSource(token);
            _token = _tokenSource.Token;
        }

        public Task[] Start()
        {
            return new[]
            {
                Task.Run(HandleReceiveMessage, _token),
                Task.Run(HandleSendMessage, _token)
            };
        }

        public void Stop()
        {
            _tokenSource.Cancel();
        }

        public bool MemberOf(string session)
        {
            return _sessions.ContainsKey(session);
        }

        private async Task HandleReceiveMessage()
        {
            using var reader = new StreamReader(_stream);
            
            while (!_token.IsCancellationRequested)
            {
                await Task.Yield();

                try
                {
                    var message = await reader.ReadLineAsync();

                    if (message == null)
                    {
                        Console.WriteLine($"{Date}: Received null string, exiting connection for ${Name}");
                        return;
                    }

                    HandleMessage(message);
                }
                catch (IOException)
                {
                    Console.WriteLine(
                        $"{Date}: Stream broke, exiting connection for {Name}");
                    break;
                }
                catch (Exception e)
                {
                    Console.WriteLine($"{Date}: Exception in receive thread for {Name}");
                    Console.WriteLine(e);
                    break;
                }
            }
        }

        private async Task HandleSendMessage()
        {
            await using var writer = new StreamWriter(_stream) {AutoFlush = true};
            
            while (!_token.IsCancellationRequested)
            {
                await Task.Yield();

                try
                {
                    var message = await _messagesToSend.ReceiveAsync(_token);
                    
                    // Console.WriteLine(message);

                    await writer.WriteLineAsync(message);
                }
                catch (Exception e) when (e is IOException || e is TaskCanceledException)
                {
                    break;
                }
                catch (Exception e)
                {
                    Console.WriteLine($"{Date}: Exception in send thread for {Name}");
                    Console.WriteLine(e);
                    break;
                }
            }
        }

        private void HandleMessage(string message)
        {
            var json = JObject.Parse(message);

            var type = "DIRECT";

            switch ((string) json["type"])
            {
                case "EMIT":
                    var session = (string) json["session"];
                    var emitData = json["data"];
                    EmitMessage(emitData, session);
                    break;
                case "RESPONSE":
                    type = "RESPONSE";
                    goto case "DIRECT";
                case "DIRECT":
                    var receiver = (string) json["receiver"];
                    var responseId = (string) json["response_id"];
                    var responseData = json["data"];
                    SendMessage(responseData, receiver, responseId, type);
                    break;
                case "CONFIG":
                    if (json.ContainsKey("session"))
                    {
                        var direction = (string) json["session"]?["direction"];
                        var names = json["session"]?["names"]?.ToObject<string[]>();
                        HandleSession(direction, names);
                    }

                    if (json.ContainsKey("heartbeat"))
                    {
                        SendHeartbeat();
                    }
                    break;
                default:
                    Console.WriteLine($"{Date}: Invalid message type: {(string) json["type"]}");
                    break;
            }
        }

        private void HandleSession(string direction, IEnumerable<string> names)
        {
            foreach (var name in names)
            {
                if (direction == "join")
                {
                    _sessions.TryAdd(name, default);
                }
                else
                {
                    _sessions.TryRemove(name, out _);
                }
            }
        }

        private void SendHeartbeat()
        {
            var json = new JObject {["heartbeat"] = "pong"};

            _messagesToSend.Post(json.ToString(Formatting.None));
        }

        private void SendMessage(JToken data, string to, string responseId, string type)
        {
            var json = new JObject {["type"] = type, ["sender"] = Name, ["data"] = data};

            if (responseId != null) json["response_id"] = responseId;
            
            try
            {
                _connectedClients[to]._messagesToSend.Post(json.ToString(Formatting.None));
            }
            catch (KeyNotFoundException)
            {
                Console.WriteLine($"{Date}: Error adding emit message to send to ${to}, no longer exists");
            }
        }

        private void EmitMessage(JToken data, string session)
        {
            var json = new JObject {["type"] = "DIRECT", ["sender"] = Name, ["data"] = data};

            var message = json.ToString(Formatting.None);
            
            foreach (var (name, client) in _connectedClients)
            {
                if (name == Name || session != null && !client.MemberOf(session)) continue;
                client._messagesToSend.Post(message);
            }
        }
    }
}