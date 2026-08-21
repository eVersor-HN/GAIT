package dev.eversorhn.gait.domain.roster

/**
 * Name pools for the simulated roster. Mixed origins on purpose — a 1,000-person division,
 * not a village. Women and men 50:50; a few humanoid synths with designations instead of names.
 */
internal object Names {
    val firstF = listOf(
        "Mara", "Ayşe", "Lena", "Ines", "Priya", "Yuki", "Greta", "Chloé", "Noor", "Zara",
        "Amara", "Sofia", "Ida", "Laila", "Hana", "Nadia", "Rosa", "Maren", "Imani", "Selin",
        "Adaeze", "Mei", "Ingrid", "Paula", "Yara", "Nia", "Birgit", "Tamsin", "Lotte", "Ebba",
        "Svenja", "Aiko", "Farah", "Kerstin", "Leonie", "Oluwaseun", "Annika", "Marisol", "Tove", "Esra",
    )
    val firstM = listOf(
        "Jonas", "Tobias", "Kwame", "Matteo", "Oskar", "Samir", "Dmitri", "Rafael", "Henrik", "Elias",
        "Levi", "Tariq", "Mikael", "Bruno", "Caleb", "Felix", "Arjun", "Luca", "Piotr", "Jörg",
        "Theo", "Viktor", "Kofi", "Ilya", "Sven", "Emre", "Dario", "Ravi", "Joaquín", "Malik",
        "Ansgar", "Kenji", "Yusuf", "Lars", "Benedikt", "Chidi", "Marek", "Tomás", "Ruben", "Idris",
    )
    val last = listOf(
        "Voss", "Okafor", "Lindqvist", "Haddad", "Brandt", "Nakamura", "Oyelaran", "Fischer", "Mendes", "Kowalski",
        "Adeyemi", "Schreiber", "Petrova", "Nygaard", "Kaur", "Bauer", "Sato", "Demir", "Wolf", "Achebe",
        "Holm", "Rossi", "Eriksen", "Yilmaz", "Keller", "Mbeki", "Novak", "Ferreira", "Tanaka", "Weiss",
        "Ibrahim", "Lang", "Costa", "Berg", "Andersen", "Rahman", "Krüger", "Moreau", "Sokolov", "Aalto",
        "Becker", "Nwosu", "Lehmann", "Castillo", "Hoffmann", "Dubois", "Jensen", "Bakr", "Vogel", "Marin",
        "Richter", "Dlamini", "Frank", "Lund", "Özdemir", "Silva", "Hartmann", "Reyes", "Bergström", "Kraus",
    )
    /** Humanoid synths: a series designation plus a callsign the division gave them. */
    val synthSeries = listOf("HX", "KR", "VT", "NM", "SB")
    val synthCallsigns = listOf(
        "Halcyon", "Vesper", "Cinder", "Aurel", "Tessellate", "Quill", "Meridian", "Solace", "Onyx", "Caldera",
        "Ferrule", "Lumen", "Saffron", "Ketteridge", "Nocturne", "Pallas", "Riven", "Skein", "Talos", "Ulric",
    )
    val units = listOf(
        "Asset Performance", "Predictive Ops", "Field Telemetry", "Model Retention", "Substitution Review",
        "Containment", "Route Intel", "Endurance Assets", "Node 7", "Node 12",
    )
}
