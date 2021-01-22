using System;
using System.Threading;
using System.Threading.Tasks;

namespace Server
{
    public interface IClient
    {
        string Name { get; }
        bool MemberOf(string session);
        Task[] Start();
        void Stop();
    }
}