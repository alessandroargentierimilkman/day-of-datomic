(require
  '[datomic.api :as d]
  '[datomic.samples.repl :as repl])

;;```````````````````````````````````````````````````````````````````````````````
;;`````````````LET'S CREATE A NEW DB ```````````````````````````````````````````````````
(doc repl/scratch-conn)

(def conn (repl/scratch-conn))

(def db (d/db conn))

;;```````````````````````````````````````````````````````````````````````````````
;;``````````````LET's DEFINE THE SCHEMAS```````````````````````````````````````````````

(def singer-schema [{ :db/ident       :singer/name
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one
                     }
                    { :db/ident       :singer/surname
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one
                     }
                    { :db/ident       :singer/record-company
                     :db/valueType   :db.type/ref
                     :db/cardinality :db.cardinality/one
                     ; :db/unique      :db.unique/identity
                     }])

(def record-company-schema [{ :db/ident       :record-company/code
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one
                             :db/unique      :db.unique/value
                             }
                            { :db/ident       :record-company/name
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one
                             }])

(def song-schema [{	:db/ident       :song/code
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/unique      :db.unique/value
                   }
                  {	:db/ident       :song/title
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   }
                  {	:db/ident       :song/genre
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   }])

(def album-schema [{ :db/ident       :album/name
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    }
                   { :db/ident       :album/year
                    :db/valueType   :db.type/long
                    :db/cardinality :db.cardinality/one
                    }
                   { :db/ident       :album/number-sold
                    :db/valueType   :db.type/long
                    :db/cardinality :db.cardinality/one
                    }
                   { :db/ident       :album/songs
                    :db/valueType   :db.type/ref
                    :db/cardinality :db.cardinality/many
                    }
                   { :db/ident       :album/singers
                    :db/valueType   :db.type/ref
                    :db/cardinality :db.cardinality/many
                    }])

;;`````````````````````````````````````````````````````````````````````````
;;```````````LET's TRANSACT THE SCHEMAS``````````````````````````````````
@(d/transact conn singer-schema)
@(d/transact conn record-company-schema)
@(d/transact conn song-schema)
@(d/transact conn album-schema)

;;```````````````````````````````````````````````````````````````````````
;;``````````LET's DEFINE AND TRANSACT ENTITIES```````````````````````````````````````
(def universal-music-italia { :db/id "temp-record-company-id-1"
                              :record-company/code "RI765"
                              :record-company/name "Universal Music Italia" } )

(def sony-music { :db/id "temp-record-company-id-2"
                  :record-company/code "RI745"
                  :record-company/name "Sony Music" })


@(d/transact conn [sony-music])
@(d/transact conn [universal-music-italia])

; refresh db value
(def db (d/db conn))

; look for saved record-companies
(d/q '[ :find ?e ?company-name
       :in $
       :where [?e :record-company/name ?company-name]
       ], db)
=> #{[17592186045423 "Sony Music"] [17592186045421 "Universal Music Italia"]}

; refresh in memory objects si we also have the entities id
(def universal-music-italia (d/pull db '[*] 17592186045421))
(def sony-music (d/pull db '[*] 17592186045423))

;;``````````````````````````````````````````````````````````````````````
(def max-pezzali { :singer/name "Max"
                  :singer/surname "Pezzali"
                  :singer/record-company universal-music-italia })

(def carmen-consoli { :singer/name "Carmen"
                     :singer/surname "Consoli"
                     :singer/record-company universal-music-italia })

(def francesco-gabbani { :singer/name "Francesco"
                        :singer/surname "Gabbani"
                        :singer/record-company sony-music })

(d/transact conn [max-pezzali, francesco-gabbani,  carmen-consoli])

; refresh the db value
(def db (d/db conn))

; query the newly inserted singers
(d/q '[ :find ?e ?name ?surname ?company
       :in $
       :where [?e :singer/name ?name]
       [?e :singer/surname ?surname]
       [?e :singer/record-company ?ec]
       [?ec :record-company/name ?company]
       ], db)

=>
#{[17592186045426 "Francesco" "Gabbani" "Sony Music"]
  [17592186045425 "Max" "Pezzali" "Universal Music Italia"]
  [17592186045427 "Carmen" "Consoli" "Universal Music Italia"]}


; let's refresh the in memory instances representation so we can have the entities id
(def max-pezzali (d/pull db '[*] 17592186045425))
(def francesco-gabbani (d/pull db '[*] 17592186045426))
(def carmen-consoli (d/pull db '[*] 17592186045427))
;;`````````````````````````````````````````````````````````````````````

(def album-pezzali-1 { :album/name "Il mondo insieme a te"
                      :album/year 2004
                      :album/number-sold 302000
                      :album/singers [ max-pezzali ]
                      :album/songs [
                                    {
                                     :db/id "tempId1"
                                     :song/code "34ddeed"
                                     :song/title "Fai come ti pare"
                                     :song/genre "pop"
                                     }
                                    {
                                     :db/id "tempId2"
                                     :song/code "89daews"
                                     :song/title "Lo strano percorso"
                                     :song/genre "romantic"
                                     }
                                    {
                                     :db/id "tempId3"
                                     :song/code "17ryafp"
                                     :song/title "La volta buona"
                                     :song/genre "light-pop"
                                     }
                                    ]
                      })

@(d/transact conn [album-pezzali-1])
(def db (d/db conn))

(d/q '[ :find ?es ?title
       :in $
       :where [?ea :album/name "Il mondo insieme a te"]
       [?ea :album/songs ?songs]
       [?es :song/title ?title]
       ], db)
=> #'user/db
=> #{[17592186045431 "Lo strano percorso"] [17592186045432 "La volta buona"] [17592186045430 "Fai come ti pare"]}


(def album-pezzali-2 { :album/name "La dura legge del gol"
                      :album/year 1997
                      :album/number-sold 415050
                      :album/singers [ max-pezzali ]
                      :album/songs [
                                    {
                                     :db/id "tempId4"
                                     :song/code "98ddiyd"
                                     :song/title "La dura legge del gol"
                                     :song/genre "pop"
                                     }
                                    {
                                     :db/id "tempId5"
                                     :song/code "73ereko"
                                     :song/title "Innamorare tanto"
                                     :song/genre "romantic"
                                     }
                                    {
                                     :db/id "tempId6"
                                     :song/code "24riahf"
                                     :song/title "La regola dell'amico"
                                     :song/genre "light-pop"
                                     }
                                    ]
                      })

@(d/transact conn [album-pezzali-2])
(def db (d/db conn))

(d/q '[ :find ?es ?title
       :in $
       :where [?ea :album/name "La dura legge del gol"]
       [?ea :album/songs ?songs]
       [?es :song/title ?title]
       ], db)



(def album-pezzali-3 { :album/name "Hanno ucciso l'uomo ragno"
                      :album/year 1992
                      :album/number-sold 819000
                      :album/singers [ max-pezzali ]
                      :album/songs [
                                    {
                                     :song/code "54rdiso"
                                     :song/title "Hanno ucciso l'uomo ragno"
                                     :song/genre "pop"
                                     }
                                    {
                                     :song/code "84aredp"
                                     :song/title "Non me la menare"
                                     :song/genre "pop"
                                     }
                                    {
                                     :song/code "90riwsm"
                                     :song/title "Sfigato"
                                     :song/genre "pop-rock"
                                     }
                                    ]
                      })


(def album-consoli-1 { :album/name "Confusa e felice"
                      :album/year 1997
                      :album/number-sold 322100
                      :album/singers [ carmen-consoli ]
                      :album/songs [
                                    {
                                     :song/code "75rterj"
                                     :song/title "Confusa e felice"
                                     :song/genre "romantic"
                                     }
                                    {
                                     :song/code "59jfece"
                                     :song/title "Uguale a ieri"
                                     :song/genre "pop"
                                     }
                                    {
                                     :song/code "60dfwpe"
                                     :song/title "Bonsai"
                                     :song/genre "pop-rock"
                                     }
                                    ]
                      })

(def album-consoli-2 { :album/name "Mediamente isterica"
                      :album/year 1998
                      :album/number-sold 173400
                      :album/singers [ carmen-consoli ]
                      :album/songs [
                                    {
                                     :song/code "57ywehs"
                                     :song/title "Puramente casuale"
                                     :song/genre "romantic"
                                     }
                                    {
                                     :song/code "12sfree"
                                     :song/title "Besame mucho"
                                     :song/genre "romantic"
                                     }
                                    ]
                      })


(def album-gabbani-1 { :album/name "Viceversa"
                      :album/year 2020
                      :album/number-sold 679100
                      :album/singers [ francesco-gabbani ]
                      :album/songs [
                                    {
                                     :song/code "84fwira"
                                     :song/title "Einstein"
                                     :song/genre "pop"
                                     }
                                    {
                                     :song/code "32stfee"
                                     :song/title "Il sudore ci appiccica"
                                     :song/genre "romantic"
                                     }
                                    ]
                      })

(def album-gabbani-2 { :album/name "Magellano"
                      :album/year 2019
                      :album/number-sold 652140
                      :album/singers [ francesco-gabbani ]
                      :album/songs [
                                    {
                                     :song/code "14feiwa"
                                     :song/title "Occidentalis karma"
                                     :song/genre "pop"
                                     }
                                    {
                                     :song/code "94qteei"
                                     :song/title "Tra le granite e le granate"
                                     :song/genre "light-pop"
                                     }
                                    ]
                      })

;;````````````````````````````````````````````````````````````````````````````

@(d/transact conn singer-schema)
@(d/transact conn record-company-schema)
@(d/transact conn song-schema)
@(d/transact conn album-schema)

;; gives errors!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
@(d/transact conn [ singer-schema
                    record-company-schema
                    song-schema
                    album-schema ])
;;!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

@(d/transact conn [ universal-music-italia
                   sony-music ])


@(d/transact conn [ max-pezzali
                   carmen-consoli
                   francesco-gabbani ])

@(d/transact conn [ album-pezzali-1
                   album-pezzali-2
                   album-pezzali-3
                   album-consoli-1
                   album-consoli-2
                   album-gabbani-1
                   album-gabbani-2 ])



;; ``````````````````````````````````````````````````````
(def db (d/db conn))
=> #'user/db
(d/q '[ :find ?record-company ?company-name
       :in $
       :where [?record-company :record-company/name ?company-name]
       ] db)
=> #{[17592186045421 "Universal Music Italia"] [17592186045422 "Sony Music"]}

(def universal-music-italia (d/pull db '[*] 17592186045421))
=> #'user/universal-music-italia
universal-music-italia
=> {:db/id 17592186045421, :record-company/code "RI765", :record-company/name "Universal Music Italia"}
(def max-pezzali { :singer/name "Max"
                   :singer/surname "Pezzali"
                   :singer/record-company universal-music-italia
                  })
=> #'user/max-pezzali

oppure

(def max-pezzali { :singer/name "Max"
                  :singer/surname "Pezzali"
                  :singer/record-company (d/pull db '[*] 17592186045421)
                  })

@(d/transact conn  [max-pezzali])








;;`````````````````````````````````````````````````````````
;;````````````HISTORIC DATA````````````````````````````````
carmen-consoli
=>
{:db/id 17592186045427,
 :singer/name "Carmen",
 :singer/surname "Consoli",
 :singer/record-company #:db{:id 17592186045423}}

; redefine name
(def carmen-consoli {
                     :db/id 17592186045427,
                     :singer/name "Carmela",
                     :singer/surname "Consoli",
                     :singer/record-company #:db{:id 17592186045423}
                     })

; transact the change and update database value
@(d/transact conn [carmen-consoli])
(def db (d/db conn))

; pull new value of carmen-consoli from db
(def carmen-consoli (d/pull db '[*] 17592186045427 ))
carmen-consoli
=>
{:db/id 17592186045427,
 :singer/name "Carmela",
 :singer/surname "Consoli",
 :singer/record-company #:db{:id 17592186045423}}

; get historic of db
(def hdb (d/history db))

; query the changes for that item
(d/q '[ :find ?e ?name ?surname
       :in $
       :where [?e :singer/name ?name]
       [?e :singer/surname ?surname]
       [(= ?surname "Consoli")]
       ], hdb)
=> #{[17592186045427 "Carmen" "Consoli"]     ; before
     [17592186045427 "Carmela" "Consoli"]}   ; after"

;; another example of historical data

(def innamorare-tanto-song {:db/id 17592186045436, :song/code "73ereko", :song/title "Innamorarsi tanto", :song/genre "romantic"})

@(d/transact conn [innamorare-tanto-song])

(def hdb (d/history db))

(d/q '[ :find ?es ?title
       :in $
       :where [?ea :album/name "La dura legge del gol"]
       [?ea :album/songs ?songs]
       [?es :song/title ?title]
       ], hdb)
=>
#{[17592186045431 "Lo strano percorso"]
  [17592186045457 "La volta buona"]
  [17592186045436 "Innamorarsi tanto"]                      ; look
  [17592186045436 "Innamorare tanto"]                       ; look
  [17592186045456 "Lo strano percorso"]
  [17592186045455 "Fai come ti pare"]
  [17592186045435 "La dura legge del gol"]
  [17592186045432 "La volta buona"]
  [17592186045437 "La regola dell'amico"]
  [17592186045430 "Fai come ti pare"]}



;;``````````````````````````````````````````````````````

; find all the albums of a specific singer
max-pezzali
=>
{:db/id 17592186045425, :singer/name "Max", :singer/surname "Pezzali", :singer/record-company #:db{:id 17592186045423}}
(d/q '[:find ?album-name
       :in $ ?singer-id
       :where [?a :album/name ?album-name]
       [?a :album/singers ?singer-id]
       ], db, 17592186045425)
=> #{["La dura legge del gol"] ["Il mondo insieme a te"]}

; or directly

(d/q '[:find ?album-name
       :in $ ?singer-name ?singer-surname
       :where [?s :singer/name ?singer-name]
       [?s :singer/surname ?singer-surname]
       [?a :album/singers ?s]
       [?a :album/name ?album-name]
       ], db, "Max", "Pezzali")
=> #{["La dura legge del gol"] ["Il mondo insieme a te"]}

;with this variant we don't get a set of vectors but a single vector

(d/q '[:find [?album-name ...]
       :in $ ?singer-id
       :where [?a :album/name ?album-name]
       [?a :album/singers ?singer-id]
       ], db, 17592186045425)
=> ["La dura legge del gol" "Il mondo insieme a te"]

; without ... it takes only the first one

(d/q '[:find [?album-name]
       :in $ ?singer-name ?singer-surname
       :where [?s :singer/name ?singer-name]
       [?s :singer/surname ?singer-surname]
       [?a :album/singers ?s]
       [?a :album/name ?album-name]
       ], db, "Max", "Pezzali")
=> ["La dura legge del gol"]


(d/q '[:find [?album-name ...]
       :in $ ?singer-name ?singer-surname
       :where [?s :singer/name ?singer-name]
       [?s :singer/surname ?singer-surname]
       [?a :album/singers ?s]
       [?a :album/name ?album-name]
       ], db, "Max", "Pezzali")
=> ["La dura legge del gol" "Il mondo insieme a te"]

;;```````````````````````````````````````````````````````````````````
;;`````````````` RETRACT ENTITY``````````````````````````````````````

(d/q '[:find ?a ?album-name
       :in $
       :where [?a :album/name ?album-name]
       ], db)
=> #{[17592186045434 "La dura legge del gol"] [17592186045429 "Il mondo insieme a te"]}

@(d/transact conn [[:db/retractEntity 17592186045429]])

(def db (d/db conn))

(d/q '[:find ?a ?album-name
       :in $
       :where [?a :album/name ?album-name]
       ], db)
=> #{[17592186045434 "La dura legge del gol"]}

;;``````````````````````````````````````````````````````````````````
;;````````````````````````PULL SUBENTITIES``````````````````````````

(d/q '[:find ?a ?album-name ?singer-id
       :in $
       :where [?a :album/name ?album-name]
       [?a :album/singers ?singer-id]
       [(= ?album-name "La dura legge del gol")]
       ], db)
=> #{[17592186045434 "La dura legge del gol" 17592186045425]}

(d/pull db '[* {:album/singers [:singer/name :singer/surname]}] 17592186045434)
=>
{:db/id 17592186045434,
 :album/name "La dura legge del gol",
 :album/year 1997,
 :album/number-sold 415050,
 :album/songs [#:db{:id 17592186045435} #:db{:id 17592186045436} #:db{:id 17592186045437}],
 :album/singers [#:singer{:name "Max", :surname "Pezzali"}]}


;;````````````````````````````````````````````````````````````
;;``````````````PULL MANY```````````````````````````````
(d/pull-many db '[*] [17592186045457 17592186045436 17592186045456])
=>
[{:db/id 17592186045457, :song/code "17ryafp", :song/title "La volta buona", :song/genre "light-pop"}
 {:db/id 17592186045436, :song/code "73ereko", :song/title "Innamorarsi tanto", :song/genre "romantic"}
 {:db/id 17592186045456, :song/code "89daews", :song/title "Lo strano percorso", :song/genre "romantic"}]

;;````````````````````````````````````````````````````````
;;``````````REVERSE LOOK-UP PULL````````````````````
; let's look at the albums
(d/q '[:find ?a ?album
       :in $
       :where [?a :album/name ?album]
       ], db)
=> #{[17592186045434 "La dura legge del gol"] [17592186045454 "Il mondo insieme a te"]}

;let's pull an album
(d/pull db '[* {:album/songs [:db/id :song/title]}] 17592186045454)
=>
{:db/id 17592186045454,
 :album/name "Il mondo insieme a te",
 :album/year 2004,
 :album/number-sold 302000,
 :album/songs [{:db/id 17592186045455, :song/title "Fai come ti pare"}
               {:db/id 17592186045456, :song/title "Lo strano percorso"}
               {:db/id 17592186045457, :song/title "La volta buona"}],
 :album/singers [#:db{:id 17592186045425}]}

; we have album->songs
; let's check the first nested song
; (d/pull db '[*] <song-id>)
(d/pull db '[*] 17592186045455)
=> {:db/id 17592186045455, :song/code "34ddeed", :song/title "Fai come ti pare", :song/genre "pop"}

; in the song entity there is no mention of the wrapping album
; if we want to start again from the song ID and get back to its album
; we can use the reverse lookup
; (d/pull db '[:album/_songs] <song-id>)
(d/pull db '[:album/_songs] 17592186045455)
=> #:album{:_songs [#:db{:id 17592186045454}]}

; !!!! do not confuse with this:
; (d/pull db '[:album/songs] <album-id>)
(d/pull db '[:album/songs] 17592186045454)
=> #:album{:songs [#:db{:id 17592186045455} #:db{:id 17592186045456} #:db{:id 17592186045457}]}

;;`````````````````````````````````````````````````
;;```````````````LIMIT````````````````````````````

(d/pull db '[:album/songs] 17592186045454)
=> #:album{:songs [#:db{:id 17592186045455} #:db{:id 17592186045456} #:db{:id 17592186045457}]}

(d/pull db '[(:album/songs :limit 1)] 17592186045454)
=> #:album{:songs [#:db{:id 17592186045455}]}

(d/pull db '[* (:album/songs :limit 1)] 17592186045454)
=>
{:db/id 17592186045454,
 :album/name "Il mondo insieme a te",
 :album/year 2004,
 :album/number-sold 302000,
 :album/songs [#:db{:id 17592186045455}],
 :album/singers [#:db{:id 17592186045425}]}



(d/pull db '[* {(:album/songs :limit 1) [:db/id :song/title]} ] 17592186045454)
=>
{:db/id 17592186045454,
 :album/name "Il mondo insieme a te",
 :album/year 2004,
 :album/number-sold 302000,
 :album/songs [{:db/id 17592186045455, :song/title "Fai come ti pare"}],
 :album/singers [#:db{:id 17592186045425}]}
;;`````````````````````````````````````````````````````````````````
;;````````````````PULL VS TOUCH+ENTITY`````````````````````````````

(d/pull db '[*] 17592186045454)
=>
{:db/id 17592186045454,
 :album/name "Il mondo insieme a te",
 :album/year 2004,
 :album/number-sold 302000,
 :album/songs [#:db{:id 17592186045455} #:db{:id 17592186045456} #:db{:id 17592186045457}],
 :album/singers [#:db{:id 17592186045425}]}


(d/touch (d/entity db 17592186045454))
=>
{:db/id 17592186045454,
 :album/name "Il mondo insieme a te",
 :album/year 2004,
 :album/number-sold 302000,
 :album/songs #{#:db{:id 17592186045456} #:db{:id 17592186045455} #:db{:id 17592186045457}},
 :album/singers #{#:db{:id 17592186045425}}}

; they seem pretty similar but in pull we have vectors in touch we have sets

(= (d/pull db '[*] 17592186045454)
   (d/touch (d/entity db 17592186045454))
   )
=> false

(d/entity db 17592186045454)
=> #:db{:id 17592186045454}

;;````````````````````````````````````````````````

(def new-song {:db/id "temp-new-song-id", :song/code "14sdwo", :song/title "Rustic", :song/genre "pop"})
@(d/transact conn [new-song] )
(def db (d/db conn))
(d/pull db '[*] 17592186045460)
=> {:db/id 17592186045460, :song/code "14sdwo", :song/title "Rustic", :song/genre "pop"}

;; not working
@(d/transact conn [:db/retract 17592186045460 :song/genre])
@(d/transact conn [[:db/retract 17592186045460 :song/genre]])

;;`````````````````````````````````````````````````````````````
;;````````````````OVERRIDE SINGLE ATTRIBUTE````````````````````

(d/pull db '[*] 17592186045460)
=> {:db/id 17592186045460,
    :song/code "14sdwo",
    :song/title "Rustic",
    :song/genre "pop"}

; let's change the song/genre from "pop" to "pop-rock"

@(d/transact conn [{:db/id 17592186045460 :song/genre "pop-rock"}])

(def db (d/db conn))

(d/pull db '[*] 17592186045460)
=> {:db/id 17592186045460,
    :song/code "14sdwo",
    :song/title "Rustic",
    :song/genre "pop-rock"}
