cir

;SCHEMA DEFINITION ; https://stackoverflow.com/questions/15586814/datomic-db-iscomponent-equivalent-to-enforcing-a-foreign-key-dependency
(def invoice-schema [{
                      :db/ident       :invoice/code
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      }
                     {
                      :db/ident       :invoice/amount
                      :db/valueType   :db.type/double
                      :db/cardinality :db.cardinality/one
                      }])
;se si mette :db/isComponent true nel datom con cardinalità many si crea il concetto di FK. senza il sistema non riesce ad utilizzare l'id dell'entita in relazione
(def company-schema [{
                      :db/ident       :company/name
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      }
                     {
                      :db/ident       :company/address
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      }
                     {
                      :db/ident       :company/bank_account
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      }
                     {
                      :db/ident       :company/aggregated_amount
                      :db/valueType   :db.type/double
                      :db/cardinality :db.cardinality/one
                      }
                     {
                      :db/ident       :company/invoices
                      :db/valueType   :db.type/ref
                      :db/cardinality :db.cardinality/many
                      :db/isComponent true
                      }])

@(d/transact conn invoice-schema)
@(d/transact conn company-schema)





;INSERTING DATA
;inserisco un azienda senza alcuna relazione con invoices
@(d/transact conn [{:company/name "Milkman S.p.A." :company/address "Via Germania 11" :company/bank_account "IT0002"}])

@(d/transact conn [{:company/name "Accenture" :company/bank_account "IT000258987"}])

;inserisco una fattura singola senza specificare di quale azienda è
(def invoice1 [{:invoice/code "I001" :invoice/amount (double 15000)}])
@(d/transact conn invoice1)

;creo una nuova azienda che possiede già fatture
@(d/transact conn [{:company/name     "Milkman-Services S.R.L." :company/address "Via Germania 12" :company/bank_account "IT0003"
                    :company/invoices {:invoice/code "I002" :invoice/amount (double 16040)}
                    }])

@(d/transact conn [{:company/name     "Microsoft" :company/address "Via Germania 13" :company/bank_account "IT0003"
                    :company/invoices [{:invoice/code "I006" :invoice/amount (double 5556)} {:invoice/code "I007" :invoice/amount (double 9899)}]
                    }])
;-> scatena l'errore Nested entity is not a component and has no :db/id. Probabilemnte perche nello schema non è specificato il db/id?
; UPDATE: l'errore si è risolto specificato nella relazione con cardinalità many db.isComponent setatta a true




; differenza tra db/ident e db/id?? -> db/id specifica una partizione sulla quale calcolare l'id





;EXTRACTING DATA: QUERY
;tutte le entità (ovvero gli id delle entità) che hanno un attributo company/name ovvero che hanno un nome definito
(d/q '[:find ?e :where [?e ?a ?v impl1 impl2] [?e ?a ?v impl1 impl2]] db)

;a volte esegui le query e ottieni l'errore Unable to resolve entity: :company/name.
;questo perchè probabilmente hai creato la variabile DB prima di aver inserito lo schema. riottenere un istanza DB nuova per risolvere il problema

;tutti i nomi delle company presenti a sisema
(d/q '[:find ?company-name
       :where [_ :company/name ?company-name]
       ] db)

(d/q '[:find ?company-name
       :where [?e :company/name ?company-name]
       ] db)

(d/q '[:find ?company-name
       :in $
       :where [?e :company/name ?company-name]
       ] db)

;estrarre tutte le company che hanno invoices
(d/q '[:find ?company-name ?company-address
       :where [?company :company/invoices]
       [?company :company/name ?company-name]               ;1
       [?company :company/address ?company-address]         ;2        ;3
       ] db)

(d/q '[:find ?company-name ?company-address
       :where [?company :company/invoices _]
       [?company :company/name ?company-name]               ;1
       [?company :company/address ?company-address]         ;2        ;3
       ] db)

(d/q '[:find ?company-name ?company-address
       :in $
       :where [?company :company/invoices ?invoices]
       [?company :company/name ?company-name]               ;1
       [?company :company/address ?company-address]         ;2        ;3
       ] db)
;NOTA: 1 e 2 non sono condizioni ma bindings per estrarre i dati. 3 invece è intesa comem una condizione (requisito della query)


;estrarre la company con le invoices collegate
(d/q '[:find ?company-name ?company-address ?company-invoices
       :in $
       :where [?company :company/name ?company-name]
       [?company :company/address ?company-address]
       [?company :company/invoices ?company-invoices]
       ] db)
;NOTA: In questo caso ottengo solamente gli id delle fatture collegate

;NOTA: Nel caso di fatture multiple non viene estratta un azienda con una lista di fatture ma l'informazione dell'azienda viene replicata come fosse una join
;["Milkman-Services S.R.L." "Via Germania 12" 17592186045422]
;["Microsoft" "Via Germania 13" 17592186045425]
;["Microsoft" "Via Germania 13" 17592186045426]

; data una company-name riportarla in output assieme all'indirizzo e ai codici fattura e importo
(d/q '[:find ?company-name ?company-address ?invoice-code ?invoice-amount
       :in $ ?company-name
       :where [?company :company/name ?company-name]
       [?company :company/address ?company-address]
       [?company :company/invoices ?invoice]
       [?invoice :invoice/code ?invoice-code]
       [?invoice :invoice/amount ?invoice-amount]
       ] db, "Microsoft")

; => #{["Microsoft" "Via Germania 13" "I006" 5556.0]
;      ["Microsoft" "Via Germania 13" "I007" 9899.0]}



;contare le invoices delle company
(d/q '[:find ?company-name (count ?company-invoices)
       :in $
       :where [?company :company/name ?company-name]
       [?company :company/address ?company-address]
       [?company :company/invoices ?company-invoices]
       ] db)


;estrarre tutte le company che hanno almeno 2 invoices
(d/q '[:find ?company-name ?count
       :where [(datomic.api/q '[:find ?company-name (count ?company-invoices) ;inizio sub select
                                :where [?company :company/name ?company-name]
                                [?company :company/invoices ?company-invoices]
                                ] $)                        ;fine sub select
               [[?company-name ?count]]                     ;aliases
               ]
       [(> ?count 1)]
       ] db)

;in altro modo ??? provare a fare tutto senza la sub select


;estrarre la company con il codice e l'importo delle invoices collegate
(d/q '[:find ?company-name ?invoice-code ?invoice-amount
       :where [?company :company/name ?company-name]
       [?company :company/invoices ?invoice]
       [?invoice :invoice/code ?invoice-code]
       [?invoice :invoice/amount ?invoice-amount]
       ] db)

;estarre le company che hanno ricevuto fatture con codice I006
(d/q '[:find ?company-name
       :where [?company :company/name ?company-name]
       [?company :company/address ?company-address]
       [?company :company/invoices ?invoice]
       [?invoice :invoice/code "I006"]
       ] db)

;calcolare il totale importo di invoices di una compagnia con nome x

(d/q '[:find ?company-name (sum ?invoice-amount)
       :in $ ?company-name
       :where [?company :company/name ?company-name]
       [?company :company/invoices ?invoice]
       [?invoice :invoice/amount ?invoice-amount]
       ] db, "Microsoft")


(d/q '[:find ?company-name (sum ?invoice-amount)
       :in $
       :where [?company :company/name ?company-name]
       [?company :company/invoices ?invoice]
       [?invoice :invoice/amount ?invoice-amount]
       [?company :company/name "Microsoft"]
       ] db)

;calcolare il totale importo di invoices delle sole fatture con importo maggiore di 6000 euro per una specifica compagnia
(d/q '[:find ?company-name (sum ?amount)
       :in $ ?company-name
       :where [?company :company/name ?company-name]
       [?company :company/invoices ?invoice]
       [?invoice :invoice/amount ?amount]
       [?company :company/name ?company-name]
       [(> ?amount 6000)]
       ] db, "Microsoft")

;=> [["Microsoft" 9899.0]

(d/q '[ :find  ?company-name ?amount
       :in $
       :where  [?company :company/name ?company-name]
               [?company :company/invoices ?invoice]
               [?invoice :invoice/amount ?amount]
               [?company :company/name ?company-name]
                ; nel where possiamo mettere anche condizioni classiche
               [(> ?amount 200)]
               [(< ?amount 16000)]
               [(= ?company-name "Microsoft")]
       ] db)

;=> [["Microsoft" 15455.0]]



 ;utilizzo dell'in che accetta parametri che saranno bindati alle variabili specificate nell in
 (def searchCode "I006")
 (d/q '[
        :find ?invoice-code ?invoice-amount
        :in $ ?search-code
        :where
        [?invoice :invoice/code ?invoice-code]
        [?invoice :invoice/amount ?invoice-amount]
        [?invoice :invoice/code ?search-code]
        ] db searchCode)

 ;with -> serve un esempio migliore che rappresenti dati raggruppati
 (d/q '[ :find   (count ?company)
         :where  [?company :company/name ?company-name]
       ] db)

 (d/q '[:find  (count ?company-name)
        :with  ?company
        :where [?company :company/name ?company-name]
       ] db)


;;~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~`
 ;functions

 ;creo una funzione
 (def fixed_cost_invoice2
   #db/fn {:lang   :clojure
           :params [code]
           :code   {:invoice/code code :invoice/amount (double 45000)}
           })

 ;installo la funzione nel database assegnandoli anche un nome
 @(d/transact
    conn
    [{:db/id    (d/tempid :db.part/user)
      :db/doc   "Funzione per generazione fattura a costo fisso - Spesa ricorrente affitto annuo"
      :db/ident :create_fixed_cost_invoice2
      :db/fn    fixed_cost_invoice2}])

 ;; recupero la funzione e la utilizzo
 (def db (d/db conn))
 (def fn_create_fixed_cost_invoice2 (d/entity db :create_fixed_cost_invoice2))
 ((:db/fn fn_create_fixed_cost_invoice2) "I0000087")


 ;creo una nuova fattura standard presso una nuova azienda di test.
 ;La funzione si occupa di creare una fattura con codice arbitrario ma con importo standard in base ad un costo non noto a chi crea questo tipo di fatture
 ;L'importo della fattura potrebbe essere definito direttamente a sistema su un entità parametrica
 @(d/transact conn [{ :company/name "Reply SpA"
                      :company/address "Via Milano 77"
                      :company/bank_account "IT00000098"
                      :company/invoices ((:db/fn fn_create_fixed_cost_invoice2) "IST77777777")
                     }])

 ;rules -> sono un set di condizioni raggruppate in una regola che possono essere appese in una condizione where di una query
 ;il vantaggio è che posso riutilizzare delle condizioni comuni su varie query

 (def rules '[[(company-with-amount ?amount-gt ?company-name ?amount)
               [?company :company/invoices ?invoice]
               [?company :company/name ?company-name]
               [?invoice :invoice/amount ?amount]
               [(> ?amount ?amount-gt)]
               ]])

 ;questa era una query precedente da riscrivere con una rule
 (d/q '[
        :find ?company-name (sum ?amount)
        :where
        [?company :company/name ?company-name]
        [?company :company/invoices ?invoice]
        [?invoice :invoice/amount ?amount]
        [?company :company/name "Microsoft"]
        [(> ?amount 6000)]
        ] db)

 (d/q '[
        :find ?company-name (sum ?amount)
        :in $ % ?amount-gt ?company-name                    ;non mi è chiaro la presenza di questi 2 caratteri -> $ è il placeholder per il datasorce (db) mentre % è il placeholder per le rules (passate in coda all'oggetto db)
        :where (company-with-amount ?amount-gt ?company-name ?amount)
        ] db rules 6000 "Microsoft")


 ;applicare funzioni definite direttamente nel codice
 (defn predict
   "funzione che predice il valore futuro della fattura"
   [amount]
   (+ amount 10)
   )

 (d/q '[
        :find ?company-name ?amount ?amount-predicted
        :where
        [?company :company/name ?company-name]
        [?company :company/invoices ?invoice]
        [?invoice :invoice/amount ?amount]
        [(user/predict ?amount) ?amount-predicted]          ;notare che le funzioni devono essere utilizzate con il namespace (user se definite in repl)
        ] db)


 ;having count custom: Non è fatta direttamente in db ma simula l'having count con tutti i segni
 (defn having-count
   "funzione che simula l'having count"
   [position query s val db]
   (filter (fn [x] (s (nth x position) val)) (d/q query db))
   )

 (having-count 1 '[
                   :find ?company-name (count ?invoice)
                   :where
                   [?company :company/name ?company-name]
                   [?company :company/invoices ?invoice]
                   ] = 2 db)



 ;NOT E NOT JOIN

 ;il set completo di dati al momento nel nostro db in memory di test
 ; ["Microsoft" "I006" 5556.0]
 ; ["Microsoft" "I007" 9899.0]
 ; ["Milkman-Services S.R.L." "I002" 16040.0]

 ;requisito: recuperare tutte le aziende che hanno fatture diverse da I006

 ;not
 (d/q '[
        :find ?company-name ?amount
        :where
        [?company :company/name ?company-name]
        [?company :company/invoices ?invoice]
        [?invoice :invoice/amount ?amount]
        (not [?company :company/name "Microsoft"])
        ] db)

 ;not join
 (d/q '[
        :find ?company-name
        :where
        [?company :company/name ?company-name]
        (not-join                                           ;-> IN ITALIANO: NON FARE LA JOIN TRA LE 2 ENTITA SE LA SECONDA ENTITA HA UN ELEMENTO CHE RISPETTA LA CONDIZIONE !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! DI CONSEGUENZA LA PRIMA ENTITA CHE HA ALMENTO UNA SECONDA ENTITA CHE RISPETTA LA CONDIZIONE, NON VIENE ESTRATTA
          [?company]
          [?company :company/invoices ?invoice]
          [?invoice :invoice/code "I006"])
        ] db)
 ;=> #{["Milkman S.p.A."] ["Milkman-Services S.R.L."]}; I006 è di Microsoft che non viene estratto perchè lui ha una fattura (oltre le altre) con codice 006

 (d/q '[
        :find ?company-name
        :where
        [?company :company/name ?company-name]
        [?company]
        [?company :company/invoices ?invoice]
        (not
          [?invoice :invoice/code "I006"])
        ] db)
 ;=> => #{["Microsoft"] ["Milkman-Services S.R.L."]}

 ;quindi
 ;NOT = La not estrae comunque le coppie di relazioni. Microsoft esce perche ha 2 fatture, una che rispetta la condizione di negazione e una no. Quindi la coppia diversa da I006 fa
 ;       si che Microsoft sia estratto

 ; ["Microsoft" "I006" 5556.0] -> non rispetta la condizione -> non estratta
 ; ["Microsoft" "I007" 9899.0] -> rispetta la condizione -> estratta
 ; ["Milkman-Services S.R.L." "I002" 16040.0] -> rispetta la condizione -> estratta
 ; ["Milkman S.p.A."] -> NO FATTURE -> non valutabile perchè [?company :company/invoices ?invoice]
 ;                                     è come un cursore sulla lista collegata di fatture che sono vuote per questa azienda -> non estratta

 ;NE DERIVA CHE -> VA BENE SE VOGLIO CONTEGGIARE LA SECONDA ENTITA (TIPO LA COUNT DELLE FATTURE DI UN CERTO TIPO PER UN AZIENDA)


 ;NOT-JOIN

 ;Rappresenra i dati in maniera diversa (come una join)
 ;dalla doc ufficiale:
 ;           The not-join clause generalizes not, and allows you to also specify which variables inside the not-join will unify with the enclosing query.

 ;     Microsoft -> non estatto dato che ha la fattura che inizia per I006
 ;           I006
 ;           I007
 ;     Milkman-Services S.R.L. -> estartto perche non ha la fattura I006
 ;           I002
 ;     Milkman S.p.A. -> estratto perche non ha la fattura I006
 ;           NO FATTURE

 ;NE DERIVA CHE -> VA BENE SE VOGLIO CONTEGGIARE LA PRIMA ENTITA (TIPO CONTEGGIARE QUANTE AZIENDE NON HANNO FATTURE DI UN CERTO TIPO)



 ;or-join utilizzata per fare condizioni in or tra proprietà dell'oggetto padre e proprietà dell oggetto figlio (bella relazione)
 ;       perchè la or non permette di andare su set di variabili diverse


 ;get-else
 ;se l'entità non ha quell'attributo allora viene tornato un valore di default (CARDINALITA 1)
 (d/q '[
        :find ?company-name ?company-town
        :where
        [?company :company/name ?company-name]
        [(get-else $ ?company :company/address "NO ADDRESS") ?company-town]
        ] db)


 ;get-some
 ;ritorna una tupla con id entita e valore del primo attributo posseduto dall'entità (CARDINALITA 1 - N)
 (d/q '[
        :find ?company-name ?a ?b
        :where
        [?company :company/name ?company-name]
        [(get-some $ ?company :company/address :company/name) [?a ?b]]
        ] db)

 ;ground
 (d/q '[
        :find ?company-name ?a
        :where
        [?company :company/name ?company-name]
        [(ground "test") ?a]
        ] db)

 ;fulltext (è una like)
 (d/q '[
        :find ?entity ?name ?tx ?score
        :in $ ?search
        :where
        [(fulltext $ :company/name ?search) [[?entity ?name ?tx ?score]]]
        ] db "Milkman S.p.A.")


 ;missing
 (d/q '[
        :find ?company-name
        :where
        [?company :company/name ?company-name]
        [(missing? $ ?company :company/address)]
        ] db)

 ;tuple -> comando per tornare una tupla di una selezione

 ;tx-ids (non prende in input il classico DB ma prende il LOG della connessione corrente)
 (d/q '[
        :find [?tx ...]
        :in ?log
        :where
        [(tx-ids ?log 1000 1050) [?tx ...]]
        ] (d/log conn))

 ;id 13194139534321 13194139534312 13194139534314 13194139534316

 ;tx-data
 (d/q '[
        :find [?e ...]
        :in ?log ?tx
        :where
        [(tx-data ?log ?tx) [[?e ...]]]
        ] (d/log conn) 13194139534318)



 (def res (d/q '[
                 :find ?company ?company-name ?aggregated_amount (sum ?amount)
                 :where
                 [?company :company/name ?company-name] [(get-else $ ?company :company/aggregated_amount 0) ?aggregated_amount]
                 [?company :company/invoices ?invoice]
                 [?invoice :invoice/amount ?amount]
                 ] db))


 (defn forEachElement
   [elements function]
   (function (first elements))
   (if (> (count elements) 1) (recur (next elements) function) ())
   )

 (defn applyF
   [elements function]
   (loop [e elements result []]                             ;devo bindare necessriamente le variabili che mi servono nel loop
     (if (> (count e) 0) (recur (next e)                    ;ricorro sul resto della lista
                                (conj result (function (first e)))) ;ma prima della ricorsione applico la funzione all'elemento corrente concatendolo al risultato
                         result)                            ;e quando non ho più elementi da ciclare allora ritorno il risultato
     )
   )

 (applyF [{:company/name "Microsoft" :company/aggregated_amount 10} {:company/name "Reply" :company/aggregated_amount 5}]
         (fn [x] {:company/name (:company/name x) :company/aggregated_amount (* (:company/aggregated_amount x) 10)})
         )

 ;aggiornare il totale aggregato di una company in base alle invoices che al momento ha nel sistema
 ;per richiamare la funzione     (updateAllCompanyTotals (d/db conn) conn)
 (defn updateAllCompanyTotals
   "Funzione che prende tutte le fatture presenti a sistema, ne calcola i totali raggrupapti per azienda, e va aggiornare il dato aggregato per azienda"
   [database currentConnection]
   (let [resultset (d/q '[
                          :find ?company ?company-name ?aggregated_amount (sum ?amount)
                          :where
                          [?company :company/name ?company-name] [(get-else $ ?company :company/aggregated_amount 0) ?aggregated_amount]
                          [?company :company/invoices ?invoice]
                          [?invoice :invoice/amount ?amount]
                          ] database)]

     ;MULTI TRANSAZIONE IN CICLO
     ;(forEachElement resultset (fn [x] (d/transact conn [[:db/add (nth x 0) :company/aggregated_amount (nth x 3)]]) )) ;provare l'apply al posto della mia funzione

     ;UNICA TRANSAZIONE PER TUTTI GLI AGGIORNAMENTI (non è carina l'accoppiata for e conj)
     (let [theResult (for [x res] (conj [] :db/add (nth x 0) :company/aggregated_amount (nth x 2)))]
       (d/transact conn theResult)
       )
     )
   )

 ;@(d/transact conn [[:db/add (nth x 0) :company/aggregated_amount (nth x 3)]])


 ;provare ad agganciare una fatturà già asistente ad un altra company usando il suo id
 (d/q '[
        :find ?company ?company-name ?invoice ?invoice-code
        :where
        [?company :company/name ?company-name]
        [?company :company/invoices ?invoice]
        [?invoice :invoice/code ?invoice-code]
        ] db)

 ;aggiungo la fattura I006 (17592186045427) di Microsoft anche a Reply
 @(d/transact conn [[:db/add 17592186045432 :company/invoices 17592186045427]])
 ;ricalcolo i totali aggregati
 (updateAllCompanyTotals (d/db conn) conn)
 ;reply adesso ha il conteggio variato:  [17592186045432 "Reply SpA" 50556.0 50556.0]]

 ;cons

 ;asOf
 (def db_yesterday (d/as-of (d/db conn) #inst "2019-09-06T09:30:00"))
 (d/touch (d/entity db_yesterday 17592186045432))
 (def db_today (d/as-of (d/db conn) #inst "2019-09-06T12:30:00"))
 (d/touch (d/entity db_today 17592186045432))

 ;since
 (def sinceOf (d/since (d/db conn) #inst "2019-09-06T09:30:00"))
 (d/touch (d/entity sinceOf 17592186045432))
 (def sinceOf (d/since (d/db conn) #inst "2019-09-06T15:00:00"))
 (d/touch (d/entity sinceOf 17592186045432))

 ;history

 (def history (d/history (d/db conn)))
 (d/q '[
        :find ?company-name ?invoice ?e ?a ?v ?tx
        :in $ ?e
        :where
        [?e ?a ?v ?tx true]
        [?company :company/name ?company-name]
        [?company :company/invoices ?invoice]
        [?company :company/name "Reply SpA"]
        ] history 17592186045432)

 ;per recuperare l'attribute name e inserirlo nella vista di storico
 ;(d/touch (d/entity db_yesterday 65)) -> guardo l'id di un entità che rappresenta un field per ottenere il nome della property
 ;=> #:db{:id 65, :ident :company/name, :valueType :db.type/string, :cardinality :db.cardinality/one}
 (d/q '[
        :find ?company-name ?invoice ?e ?a ?attribute-name ?v ?tx
        :in $ ?e
        :where
        [?e ?a ?v ?tx true]
        [?a :db/ident ?attribute-name]
        [?company :company/name ?company-name]
        [?company :company/invoices ?invoice]
        [?company :company/name "Reply SpA"]
        ] history 17592186045432)

 ;sorted

 (d/q '[
        :find ?company-name ?invoice ?e ?a ?attribute-name ?v ?tx
        :in $ ?e
        :where
        [?e ?a ?v ?tx true]
        [?a :db/ident ?attribute-name]
        [?company :company/name ?company-name]
        [?company :company/invoices ?invoice]
        [?company :company/name "Reply SpA"]
        ] history 17592186045432)


 ;relazione inversa creata automaticamente da datomi senza l'esigenza di specificarla nello schema
 (d/q '[
        :find ?company-name ?invoice-code ?invoice-amount
        :where
        [?invoice :invoice/code ?invoice-code]
        [?invoice :invoice/amount ?invoice-amount]
        [?company :company/name ?company-name]
        [?company :company/name "Microsoft"]                ;-> si arrangia datomic a capire come risolvere la relazione inversa perche ogni volta che creo una relazione lui indicizza entrambe le direzioni
        ] (d/db conn))





 ;TEMP ID

 ;usare il temp id per calcolare automaticamente il temp id. prima e dopo la transazione il sistema mostra gli id creati.
 ;nell API serve per ritornare al chiamante l'id degli elementi appena creati
 ;il vantaggio di usare il temp id è che posso creare ad esempio una serie di fatture (in questo specifico caso) e collegare i loro id alla nuova compagnia che sto creando
 ;e poi salvare il tutto in un unica transazione
 ;i 2 vantaggi sono:
 ;                   -salvo tutto in un unica transazione. altrimenti avrei dovuto salvare le fatture prima per ottenere gli id temporanei e poi salvare la nuova compagnia in
 ;                   un altra transazione. A questo punto sarebbe difficile rendere l'operazione atomica (1) e "sporcherei" il concetto di cambiamento del db che dovrebbe essere ricostruito anche
 ;                   considerando un insieme di transazioni consecutive (2)
 ;
 ;


 ;queryng by entity
 (def microsoft (d/entity (d/db conn) 17592186045428))

 (def microsoftLoaded (d/touch microsoft))

 ;17592186045425
 (def milkman (d/entity (d/db conn) 17592186045428))



 ;importante sapere le best practise per usare datomic
 ;https://docs.datomic.com/on-prem/best-practices.htm




 @(d/transact conn [{:company/name "Milkman S.p.A." :company/address "Via Germania 11" :company/bank_account "IT0002"}])




 ;GLOBAL SEACRH
 ;query di partenza per il log
 (d/q '[:find ?e
        :in $ ?log ?t1 ?t2
        :where
        [(tx-ids ?log ?t1 ?t2) [?tx ...]]
        [(tx-data ?log ?tx) [[?e ?a ?v ?trx ?op]]]
        ]
      (d/db conn) (d/log conn) #inst "2019-09-19T09:45:45" #inst "2019-09-19T10:05:30")

 (d/q '[:find ?e ?transaction-instant
        :in $ ?log ?t1 ?t2
        :where
        [(tx-ids ?log ?t1 ?t2) [?tx ...]]
        [(tx-data ?log ?tx) [[?e ?a ?v ?trx ?op]]]
        [?trx :db/txInstant ?transaction-instant]
        ]
      (d/db conn) (d/log conn) #inst "2019-09-19T13:00:44.223-00:00" #inst "2019-09-19T13:05:44.223-00:00")

 (defn get-log-data
   "Ritorna dati sottoposti a transazione in un intervallo di tempo"
   [time-start time-end]
   (d/q '[:find ?e
          :in $ ?log ?t1 ?t2
          :where
          [(tx-ids ?log ?t1 ?t2) [?tx ...]]
          [(tx-data ?log ?tx) [[?e ?a ?v ?trx ?op]]]
          ]
        (d/db conn) (d/log conn) time-start time-end)
   )

 (defn get-log-data-with-time
   "Ritorna dati sottoposti a transazione in un intervallo di tempo suddivisi per intervallo puntuale"
   [time-start time-end]
   (d/q '[:find ?e ?transaction-instant
          :in $ ?log ?t1 ?t2
          :where
          [(tx-ids ?log ?t1 ?t2) [?tx ...]]
          [(tx-data ?log ?tx) [[?e ?a ?v ?trx ?op]]]
          [?trx :db/txInstant ?transaction-instant]
          ]
        (d/db conn) (d/log conn) time-start time-end)
   )

 (defn normalize-data
   "Funzione che trasfroma il risultato della query in un array piatto"
   [elements]
   (applyf elements
           (fn [x] (nth x 0))
           )
   )

 (defn applyf
   "Applica una funzione anonima ad un set di elementi collezionando in un array finale il risultato della funzione anonima stessa"
   [elements function]
   (loop [e elements result []]                             ;devo bindare necessriamente le variabili che mi servono nel loop
     (if (> (count e) 0) (recur (next e)                    ;ricorro sul resto della lista
                                (conj result (function (first e)))) ;ma prima della ricorsione applico la funzione all'elemento corrente concatendolo al risultato
                         result)                            ;e quando non ho più elementi da ciclare allora ritorno il risultato
     )
   )

 (def configuration (list :db/id :company/name :company/address :company/invoices)) ;andrà messa in qualche cosa di configurabile (anche un file)

 ;se ha solo il db.id scarto il dato (contains? per vedere se contiene :db/id e)
 (defn filter-data
   "Funzione che filtra effettivamente i dati datomic in base alla configurazione. La funzione esiste per facilitarsi in caso di modifica del sistema di filtro"
   [configuration entity]
   (if
     (and (= (count (keys entity)) 1) (contains? entity :db/id)) ;se l'entità contiene solo l'id allora è l'id di una transazione quindi ritorno nil altrimenti estrai le chiavi
     nil
     (let [filteredEntity (select-keys entity configuration)] (if (and (= (count (keys filteredEntity)) 1) (contains? filteredEntity :db/id)) ;se l'entità ha ancora solo l'id o è filtrata male per via di configurazioni assenti oppure non ha i valori necessari. quindi la scarto
                                                                nil
                                                                filteredEntity
                                                                ))
     )
   )

 (defn extract-datomic-data
   "Funzione che riceve in input la configurazione dei dati da inviare ad elastich e tutti gli id delle entità coinvolte in transazioni in un periodo. Ritorna una struttura pronta per essere manipolata e inviata ad elastich"
   [configuration ids]
   (applyf ids
           (fn [x]
             (let [entity (d/touch (d/entity (d/db conn) x))] (filter-data configuration entity))
             )
           )
   )

 (defn extract-datomic-data-with-version
   "Funzione che riceve in input la configurazione dei dati da inviare ad elastich e tutti gli id delle entità coinvolte in transazioni in un periodo. Ritorna una struttura pronta per essere manipolata e inviata ad elastich"
   [configuration ids]
   (applyf ids
           (fn [x]
             (let [entity (d/touch (d/entity (d/as-of (d/db conn) (nth x 1)) (nth x 0)))] (filter-data configuration entity)) ;0 è l entity id mentre 1 e il time della transazione che l'ha modificato
             )
           )
   )

 ;chiamare la funione di estrazione rimuovendo i valori nulli (nulli perchè precedentemente filtrati)
 (remove nil? (extract-datomic-data configuration (normalize-data (get-log-data #inst "2019-09-19T13:00:10.662-00:00" #inst "2019-09-19T13:06:10.662-00:00"))))
 (remove nil? (extract-datomic-data-with-version configuration (sort-by second (get-log-data-with-time #inst "2019-09-19T13:00:10.662-00:00" #inst "2019-09-19T13:06:10.662-00:00"))))