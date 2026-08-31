package za.co.jpsoft.winkerkreader.utils

object AfrikaansNameAliases {

    // A map where each variant points to a canonical root name,
    // or groups of equivalent names mapped to each other.
    private val aliasGroups = listOf(
        setOf("piet", "petrus", "piepert"),
        setOf("johan", "johannes", "hannes", "hanu", "jan"),
        setOf("willem", "willie", "billy", "bill"),
        setOf("jacobus", "koos", "kobus", "koelie", "jaap"),
        setOf("nikolaas", "klaas", "nico", "nic"),
        setOf("abraham", "abrie", "braam", "biebie"),
        setOf("hendrik", "hennie", "riana", "hendrieka", "riekie"),
        setOf("elizabeth", "elsabe", "bettie", "elza", "liesel", "lisette", "betty"),
        setOf("maria", "marie", "martha", "martie", "riekie", "mia"),
        setOf("cornelius", "neels", "nelis", "corne", "neil"),
        setOf("frederik", "fred", "frits", "riek"),
        setOf("balthazar", "balties", "baas"),
        setOf("catharina", "ina", "rina", "katryn", "karen", "trudie")
    )

    // Quick lookup cache: lowercase name -> set of all its valid equivalents
    private val lookupMap = mutableMapOf<String, Set<String>>()

    init {
        for (group in aliasGroups) {
            val normalizedGroup = group.map { it.lowercase() }.toSet()
            for (name in normalizedGroup) {
                lookupMap[name] = normalizedGroup
            }
        }
    }

    /**
     * Checks if two name tokens are considered aliases/shortenings of each other.
     */
    fun areNamesEquivalent(name1: String, name2: String): Boolean {
        val n1 = name1.lowercase().trim()
        val n2 = name2.lowercase().trim()

        if (n1 == n2) return true

        val equivalents1 = lookupMap[n1]
        if (equivalents1 != null && equivalents1.contains(n2)) {
            return true
        }

        // Handle substring/prefix matching for short forms (e.g., "Christa" matching "Christiaan")
        if (n1.length >= 3 && n2.length >= 3) {
            if (n1.startsWith(n2) || n2.startsWith(n1)) return true
        }

        return false
    }
}