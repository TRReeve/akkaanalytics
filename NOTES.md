# Notes

**Key Files**

src/
Resources/
    application.conf - Application configuration for key parameters

scala/
    data/
        DocumentNode - Class Definition for an individual Document processor
        Protocols - case classes and JSON class extension for Spray datetimes
        Supervisor - Co-ordinates job, receives Messages from Message Queue and holds routing of Documents -> Actors, visitids -> Documents, and Orphaned Messages
    messaging/
        FileMessageQueue - Implements read method for actor
        MessageQueue - Implements message queue actor for receiving Read Requests


**Process**

1. Initialise Supervisor node and request it to ping the Messages Actor every 5 ms using Akka Scheduler

2. Akka Supervisor Pings Message Queue, converts messages to their cases of Update/Create
- If a Created Message, Checks for a processing actor in the map of document ids to actors and is dispatched there
    - If No Actor is found then a new DocumentNode is initialised then it is sent to that node.

- If an Update Message, Checks for an entry for its visit id in the map of visitids to documents.
    - If None Found then it goes in an Orphan queue for updates whos corresponding document is unknown
        - This Orphan queue is flushed periodically
        Unimplemented: Entries in this Orphan Queue that are older than a certain time period should be removed



3. Document Data Handler Actor holds information for each reader in a map. Message Updated is compared with new messages
for if the new message is more recent. If an Update Message is received that is less recent than the one currently in store then it is ignored

4. To Generate KPIs An analytics request is sent to each Document Node and a function (generatereport) returns the
kpis in an Analytics Report Object. The Processing of the data is distributed for each document.



**Receiving Messages**

                                        FileMessageQueue
                                              |
                                              |
                                              |
                                              V
                                          Supervisor-------> DocumentNodeActor(documentx) ---> Updated/Created Messages
                                                   \ -------> DocumentNodeActor(documenty)  ---> Updated/Created Messages
                                                   \ -------> DocumentNodeActor(documentetc)  ---> Updated/Created Messages

**Building Kpi Report**

                                        FileMessageQueue
                                              |
                                              |
                                              |
                                              V
                                          Supervisor ------>  DocumentNodeActor(documentx) ---> Map(data)    --> Export(KPIReport)
                                                    \ ------> DocumentNodeActor(documenty) ---> Map(data)    --> Export(KPIReport)
                                                    \ ------> DocumentNodeActor(documentetc)  ---> Map(data) --> Export(KPIReport)

**Potential Improvements**
- Use Cache or Key Value Store for holding persisted data and retrieving that when kpi request is made.
- Proper Logging of Orphan Flushes.
- If One Document gets an unusually large amount of activity there is no means to redistribute that work, on other hand we'd be more likely to be using
an actual cache or db on a large enough scale that this causes an issue.


**Other Approaches**
- A Round Robin approach was tried where all messages would be sent to random nodes the upside was that it didn't have single point of
failure in the supervisor node, on the downside it was not very efficient sending messages around constantly.

- Some experiments were done with parallelising the Message Reads, but this needed more Supervisor Actors to cope, and then there would be an issue
of updated messages not knowing what document they would be going to (Hence the round robin approach as a potential solution).
