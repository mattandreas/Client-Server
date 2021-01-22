using System;
using System.Net;
using System.Threading;
using System.Threading.Tasks;

namespace Server
{
    internal static class Program
    {
        
        //https://stackoverflow.com/questions/7863573/awaitable-task-based-queue
        private static async Task Main(string[] args)
        {
            if (args.Length == 0 || !int.TryParse(args[0], out var port)) port = 54004;
            Console.WriteLine("Server on " + port);
            await Task.Run(() => RunServer(port));
        }

        private static async Task RunServer(int port)
        {
            await using var server = new TcpServer(IPAddress.Any, port);
            await server.StartAsync();
            Console.WriteLine("Ending");
        }
    }
}