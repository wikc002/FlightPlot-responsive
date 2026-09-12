package me.drton.flightplot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Data-driven field translation; keys remain the native dynamic log schema names. */
public final class FieldNameLocalizer {
    private static final Map<String,String> moduleMap = new HashMap<String,String>();
    private static final Map<String,String> tokenMap = new HashMap<String,String>();
    private static final String DATA =
            "H4sIAAAAAAAACn19yVYbSbPwOnmKu+xvcc+PhHtaCk3oMxq6SgjERqeQClBbUqmrShh6he3GYDCD2wPGYINtbHC3DXhkhpdRVklvcU/kVAPyv2" +
            "isiMjKjJwiY8rsKhpRlaJWK3QjvLeN7840L19aW7NdVTRaKY+Nm4Vu1H79ovXyvrW0g+e+dVXRWM0wCuWaMlHoRvGULNunf+GVObx/1pr7B8h1" +
            "o6ArN4GYkfHSJt5ZsB4fWIt7XVVUripjamFM1aC19Qt8ZxlvHNgrd0WBatkwyoSZ5ukpnn9p3dtpvbwPBK2kFrqR/fnUPt20dl/is2WCNTXdAP" +
            "yjr9bGiT3/zZq+1VVFdc0oFE29AvWcL9rne4J54MxQa4amA+1sy/prE6/t+tjUi2VgoX3rjbW007p4iGfeANa8wTomZa/7vgiFwwjPb7WnN/HJ" +
            "29beS0D1SUGEdy6t6VutuX/wygO8fLs1fQcoCTmAWnPHrblvrdl/8Pxua/8unvuXUq59l/LjdynSdygRuRf+/G8vAKk4au38jU/eirEIZbOMQQ" +
            "AGhmKodTGD59/h+c3WnfOuKuoNSWlkHTzCSwu0T72hLBnqj1tdVRQOJZG9fmRtnACQjLApww/Om6fbgMoOpFD736fuJqOShNqP1lr7+wDkUPNk" +
            "oXkKhHhGRnhm1165g/eeNc8XAZWXUHttuvXipHkKCyuRHEDWnX1resf6utCehflPhuLIfn0Lz69T/pLpSJQvV75GknIcWd/mrFvQZCYRQZQZvP" +
            "6idUhQaZktkq4qkkLZKAwTnUjBthRLRZD1daF1+JzyIoXTAwhWxuxJe/pZ+9bDHzKDyf8QQiJFCDNvGEEKA15OJFHz9NLe2BRrJguDgTcO8Pkr" +
            "Zx2lQ6h1udTa+fv/NS92rNMHrZ2/AS0lUWvndfvRLbHEQ5KcQfa7k/b0JltvWTmM2Ao4uIOXb//Q2vnbWtqB1ntDUsSZSDy/S+voDfez6cSLj5" +
            "vnD+1HX/HSAiGk/NIgHIpFUDiU+p9YxJo+tU8uCS5FcK29C7xNC6Vi8MddQoY/9injMixLOUA0z06sDZjlcFbq51Oyttvan7eefOiqokjgJ0RH" +
            "JfATXQ6RniDD9AQZJpxE1pNjvPKgeb6Gz97am+/aT78CIdafZWWtr3v27WPAJWWGah69wvdnBEcROYas55et83Nr9Q2+XHURsv3OpNOJAvSAYM" +
            "1a3bLfv8XLh4LHAcGkoHFuo6E+CeHtJ+07u3TT4c0TPHsXKJFskEk2vLSA59d/sLaO8OWdIExdVA7DHLUO7oA4+gqzE5XDQwxn3XuHPz7uqqJY" +
            "oj+KrCezzdNv1sPF5vkG4JJZZG2d4bNl2FPH9wCVCkfhU/xhBa9/sbZgg8QzIdh+9qcLfPKWwPlBIrtvLVOJHpdCFOGVeXFpqA/Q/wPicPsLxc" +
            "gcYz3dxEvQu/hAIoRgdvfPhLCJDyQyDCe2Xl80lIU9jue37Dsf8NoubPl0MowSaby4jLf/slfuMqzcG0Hthx+tp85mSsi9fQyHL++0Dj93VVF/" +
            "JJpF9vPp9tpd6/U0HbtkKAf/9ZdrN8REJ0O5MEcK6ZUMDyB8wQ4OvLYrNl4yIUf9p1MylUXNkwd4+cC6f7t1DrIzmU6xObXXH1hLOwSV7WUnlf" +
            "Ucdkfqer4bRa/HeuhQN88OWnsvuykhcIUQ6KqidKhXQu1bl+1nG+1bl/arA4KL/JfhYEU8hplIh3JxhqMrGHAxhGfuWV+hE2kpnkJ4adO+fSwm" +
            "IANSBi/fth5DrZlEJAR/mCyhhxssyEwiEnURmkcbeOUBI6TcX9xfdQgZ9xeX+83TA0aQXARr9x/r9BkjyC5C6/y9U1Xe3QYZHkLoR7CAZ++21x" +
            "ZbKzABmSSypndad87F+GfSgxIZ/5MVMZsZaQi1Lh/AHufKAMVKDO1b9Bk5HPHoFD80jxYIA3I46idsMELKS8D3Vykhzatq7dy2vi44VaWjfgKr" +
            "Kp3yEpyqsj4CEVeEBscwnVY6auSYi4CYap5tgSxYhvmWQv151F6/a58+pAJTCsl9bAk5etKtN/jkkBITfiIcFeSQlXrDfYgeHM4HveEEw7nKpZ" +
            "1yy/bOKcFJfS6h63wuJRy0U0M4EURSmJ63QhZK4TTHzp5QLOwdKZzu8aGDgI5m+vjY7e/jwz28dBcvfyKUXBC1zt/DEftyl5bN9TiYHorpQ0zt" +
            "83+d70F0gfopMSnmdAZ6f7lK0a6uU8WJolOuEeFqthTPEOnrDFE8kwCEMzjxzH9JieNP1j0yxQm5DzQokMTiq4ScYDjnw2S8z1GrnKLJeMJBO6" +
            "XTsT4mWJyimaQzTABLqT7UXj9sXVw4ZaRUguGcuuT+fjYX+ON0+86udfAFH4Msl+R0EFlLb6xH36yNEzqHZEbkdI8fT+ZFhn4RfPPyJV4+xGcn" +
            "bDxz6T7U2rnb2rnXnr1v7xKNSDA1GAqCXMQ7C1S3Im0Mhno8SNLAYLoPtV8v4ZUHdMuJOuQQcutucm+mD8n/29swnBK9UpSiBOdCKZZhA3hp7u" +
            "+SPprQbOWwhFp/PbM23rcuV1ov7wsJJyciEXYStS7AOBCyTAYp66Y430ig+qydtO6cty4f0QUnZ0PX+bm3NSeEqjwAi/ngS/v5K1jp5DjMSgkw" +
            "Nt7i5QdUng70DgXRQG9Fm4TxHEglsohonKAX5RK9UWTd38fzUDKXkDNsdsTBlJOzvQxn737Ge8+6qmgwko4je2OhvbprL8DuGUykwsg+fWGvP+" +
            "2qoqHrMXqA0i7B9h+6Hgu6UMDI0PVYjwsF0zp0PXbNhbpGUT+6UD9SVISgxFgOXY/JBOM+Roau/0ZP8fV1PHOHnqtD17P0y9Vv7VU4qoeu5yir" +
            "oMoe7lFWPXoBaARD13PdrlLd3/swF3ShaB89qgSUGghJWdQ8+oSXX9O5CdC5AVouIafZWOOV+9YHMHxzCTnHdwyRdDDc0VQYtc738NmyffbE3r" +
            "rFexwjbNKholzGkoBpvVjEK3PCKhu6ns0Bmg4DZberiibU8XKxohYU0yybjZLq2KecUtGKSqVQ14yyWdZqiIoJsVR4qbGKNuIpNrOLP06LYkrR" +
            "bCimphe0hllvmAaimhwYIESCuIsUtZqpaxVXGWEWjiimqepTBcNUzIbBTCmxiZiboahVR8o1tYSon8S9PgS3dcNhFUQ451M1zHKVcMGaoJqgWx" +
            "011Ypa5WR2pnMar5/zR9VRP1VXTNUoGKpZ18o1k1m/9tIsWHV7z6gy4J8XpzjzcLjK8p6IMgVTL9crqslPWVfZcq3eMAt60e9rqSq1hlLhY++0" +
            "Zt1bAOuVTIC7Hq1ulmFdjFa0m46WWyobplIrArcwFfwUdc2AUtaNugqTQwQ7DPmN0WChXKtpEwp0woBFGsRz69aTAzx91lVFxXqjoiklFM4MtL" +
            "5sgrJvIqVYVCuOA6jLRKNKuWIooyrC2x+tpZ3m5XNrfhvwFWWsoOhVZs6zw9/kzjZwc3kdGCYirjZYIuOqUjHHEVUBxNFP26uXzeI4oqo1B8nM" +
            "MhydUg+lYNSRvb5HNLIORXxUwPMlytangyloIxOqjuhBeJXqVMVJulapIKrvM4gyS1GCE0FwquhUwkfsMtGEWtGKZXOKqWYujFNYkKaUm0xRow" +
            "BlhWJEQxzvfN+hgI8GK6NiFibUCvODOcujPGlO1VVEnV32x1P8YqHLpK7VkmqqRVMtEecqPvzEbFeTeFbBs1nS6o6UoGY7Xt/CH1agjK41aqUC" +
            "XdR4Aya1y0TjKqwuxgTAml7+U6uZSqWgFItwfOPjz8wBYKKKwoSvffKeYbTaGEOdLlNUrVE1JhBe/Jda4TAV5o0CDCW4A3j3GY4vXUFyL12zXF" +
            "XHlDqiRwGcAyZq1EuKqRRgtyBr/Yv15EDslAm1UighcPocLbhnt6BS5IYHWQMkvr96dR1AxylWdHxC1akYARp+cdte/yJoSoUNihjEYqhYROA2" +
            "+vzKXj+C2eBlR8uTsknWEJ1eKnFNNB66OtjjqlIq18YQ9RF3mcgIdWAsdJWhCbUS6TAM0Q6jkPIPQrlWUieR/eUVPnsMTap/IHyyjJcPYcpUxY" +
            "ATc2kTr2/B9Cj6mGoWRkZhVAqTfKdtnDTPHw61zr+4pZ637JSnbL5zWVjQvNIhIR9cRF5LvhPxT0Yc9hNh7kdGO7Drb58VvMprp4KqUyMJXNz5" +
            "Xo2qUyMt+P+p8U9PweHvFISd5ZM7njJCbnUUTqOVqQLss4JRGJkqFBu6rsLZvXnSPFkCteXrLfvZX+zcoYqpiapKuVaoFhsFU63WdcVs6CpqHp" +
            "0mwwPW0TvaNoRDyF4hxpF7doEyouhaQamYjEz9z2L7iBJ1XTWMhq5eKeouBDyojAl3GQ8rXB+b0CqmMsYLMrWM+rZpwbEpXWNU4exnpKoyxr97" +
            "fQtvnDjogjrJ+aNuXE8BUxstjKgV7aarG82jBevJcTYdE502qopuAp+FolJXiBwabVQqheI4zCIzuSjD1ulLPDOD944h1NHxy7qqF9Wa5yN871" +
            "3zfM1eu8Bzd639R52/01WY2tpYhy87NccWi7swXTKeYu4J8nSDT5BTlE+Pr0Y6PaXy6GiBTN7hHp672774y9rYdA62otbQDZWcbMTgpDJzvDRW" +
            "MEpMhsLqn71Lz5FxOCvdElcclN4jsKErxakO56DrCKyNeQ6/gqGYhuf8I2euU5lPhJPdd1NVbxCXzINd+o3AFqoGI+C7M9b+v/bO3yC7gX233B" +
            "fsi2NsEnlkkMBPoXxH/J/II2HcRx7l23fMcB0atQ6f229PaVVXG73anK8hcoK5BETIp3mbKFRxaSihihlKyhB/ObQWHjlo2jL8oi3CL9pSl4nC" +
            "DV1HYm0ClNVMZH/Zb+29dNCgYjdPX9tbAEVUI0NUZ5+uG1ENCXRUn14ZUY28TxADtmyYzvhESQvUoUMbiYd1w79k41O6K7ppoj6m3BPFSGgMfX" +
            "7toE/TiT5Jl6oYzQTCey+aF7CBEiVkbc229u/C73AoXVBKJZCwBAC38PPpLhP1u9d2v3tt92s1F6BCY/bJDkSUTZRUDYPsXOp1MlESuiosFYBS" +
            "jSpD2GdPqEaRkv1bJT06aqgmYl5fE9EpoIMPXjMTZQjLNBzWZaLfAkg4UQIABx04CHCPA/cAfM2BrwHcUCqo9WWXCjcys3ROaWuyUq1X1AEZtW" +
            "dnra1DegT+gC/27J2//wP0esmxJOQ/Gop+A+GTR/aHR6Av8LUkMzObT57cGDGmDIQ/rFDLu8tEWbVaR0IkZstVlam9DIIgJW2cyoD/MPyAwDtM" +
            "dcLllEpDRWComiin0qVCN7RgP6dVTCTELaxm5qQmIzGEhgCL8l0mGkbDoPgOOfsWaEregaGUMuzAUL4+xOxDKFzPMwBK1ocZAMUmhtiIQrGJPA" +
            "Og2MQwA6BYdQis9vbsMhSr5hkAxarDDIBiI1ppaggxLY/BeQZDYfALDDHHwBCD8wwGenxKH3Li/lAiPqXnHQwrM+xgoNVQsegbnFCx6BueULHo" +
            "G6CkMjZE/OgbJ/BJUhnLMxDKJ5WxYQZC4fSoMcT2CRROjxp5BkLh9KgxzEAoLKkVEKL2+hHePxYyM56RARvPyALVCwifPhahnxLLmOPIh/j1AX" +
            "6wInBZgiPhJYFLExwJJwpcuI8anfj8Ib63KNB9Y2Z6QmeCvvX2rr3+BHgEeQRBaC6S4iCTAMElURpK0CZEoTQUYjheLtJZnHcU5h1EeVTXpQyT" +
            "DUwekeANJbl2C8eGvKdaRDV4lW50umG68oOoj9FEGWTtP6JSO+N86ODgKwqJTyTHayK5PhE48gmVbPyTvIsn3s08YYj2hJfLjieQ9em4vUqcoD" +
            "NvgO9eONhI1lDr5Rt865LSSeG0qzD7vg9Zt9/jWxuiVFgykT33Hi/OUjMkQhCEFTc6FEU0jOwZs+j1GDjfvLgKwouzEOOd+0pg3TWsYkqiekd0" +
            "LOFgrdNtsNu5BRzqYyuSxVoA86cno6v96gFj1lUL+GL5ueuk7hCA2+ZOWeIcI7QUosHxTrSGKVfUm/7Fgpef4vtPGAN+meOXOD55E6qNJZVJZG" +
            "1M4+0dOpkMW64R7MGyg9Wr4XG1eMPg/snX09YmWQl6lbo0PGlIJgo1TC2iFnni0csDTE7Y0IRSriC8vG8/gnXQSzP0Pm51AxBgABzkvUEGwCne" +
            "28MAOMJ7rzEAzu/eHxnwIwA/MeAnAH5mwM8AJBB+vpxIDsBvpXgD4ZWl9jToO72KyQ8+t7XRW7mB8PNVqpX0aogmLuGZz+3VD4ABnzrNZTJR75" +
            "Spwmm+2ponORImCiOaqAM/A4jme0GnwkEGQKfCPQyAToWvMQA6Ff6RAdCp8E8MgE6Ff2YAdCr8CwN+AeBXBvxKGu3mrcLAhgOCCcJFgLMRIHwE" +
            "OCMBwkmAsxIgvAQ4MwHCTYCzEyD8BDhDAcJRgLMUIDwRlRMGIhEBkKiABBYqYJgrN4uPW0uHV1SccCc9vVKujvjkR7hSriN8b976/Kq195JNQq" +
            "VhIvvtKeSgrhGhA0EMQdUqjWrNQHiOTXJYa3joejGq65qOwlKYxWBNxPcu/OxmKWYwwJEAA2B4I0EGwOBGehgAQxu59iODrv3IDqVfeTW/djNM" +
            "oIcXCvTwUoFfeLHAL7xcMMjLBYO8XPBnXi74My/XE+DlegK8XCiS5WKNyw+8f0Y7HrkqdIWkjISGiJnViZInZlcnyjAxwzpQ+sMsJ679+BJMYm" +
            "p49atgZ3y2v7yiaILjdgj/khspDM4wr4CwCyIZ3gt3Bgch0C6wZIJXL6jmH8lQ/q+gKfN+tEQCKEcL7bVFdoBd0YhYc1mP+zqSi2Q9erdbfkdy" +
            "nGf/QOUoz1fQlOcraMrzFbRa+U41auU7FamVzlU56g5DgKDnIj6ijioNMNlPn7b2tqnJEQFz4/Rb6xJsnYhaMRXkDKbQQojFPDrKTDxIjCE7ra" +
            "qVWO3Nyz3rw2vA1RHN1oTfehliTO13HyFTwEQRkw259eFN+zEIaD6s8DPUQaOIxh2L29HqrsfgdIRQNDkghTkGhHKNEw6W3YR0w6yQMPfbU/uC" +
            "9T1aU1Dz/BIyp0FXRC69US4yyD57Zz26AJxhslguAOYkwssP2hBGZWIpOgEuN5YdbaJYCFGFxd2dWGiCY48/4+ezlI0YZHbNQN4fDxvFYggU8L" +
            "dz8Dsx5I85xRJ5Psk089pEsX5wIeDL1eb5EoDJGmuIjgRBTbpQ2zuAyrBmhP4akxkreO8enoE5i8l1RBWp1sFHApsMFgpFLMsr5uMdy07VVYNl" +
            "sjpsDxjKSEVlOgY4kWhhpVyJVZQxg48CDxvFypMQEVs/gfwRE7EyrJek05UxBgOg6UUofnYCkX4CVxWT8QCwrlRVhI9Ix8X04e2PrS+gLcX7nJ" +
            "UmNMp4358OVqiTcZK0JbRIyIOPZ+TOZls8IxOXQTwji9GJZ+RBx6tIEYVw3/XCYCiRJSWJCmd/uIcvZhg5niUZv8Ij1bo8BsqQwx6xgPMOTOzf" +
            "YQcGvTJeKxF3Bq1GODW4Jg0/Q/F+cjRvHDg4VRfWH98hfXWGEVGcREgan6rDP/gWJCIQX5YswR+8vUgnDFL5haMrMoSG6PHnxOoBnUf5DmguKe" +
            "ydfTxHvo+GPSghgRLRfg+hvbZCV4T/NgJefIxnQBNMJAdAoCSSAz6BAoRyjRNcAiWRFP4AD5dJ4RfwooV/wIPOROCkEiehl+Y9Jb00bo10oMlI" +
            "gM2j6dbsF2saVnwix9tip5v7G9/p5qXxtjrRssiHhXSNfbDOEp6D0fuV+0jzUtxnmofSeal0Xin50CCXjm70MBruUHishvDluf0YRECiOsauOA" +
            "FQY050sc/7AwnUHxDrrz8dRdbjOXtp1lrcxNsgG/sl1H58SYMf/coI+Hy3Zu0PsBX6FcNM1H5Xiyax31aWrM+71GAHSlJVDFC1KYUszisKN5Qb" +
            "gBi7ysuRIDuhTKk6wh9vE+9vDXjgruDamDnugt0+4/Ko6diYeHGWamf9WtlUdeYUAN9WKF6IDcgJSI3neaQ0LwyIfE05F3pMlBQ71Y10paGKAU" +
            "3ypeUpSQSUwAgZlRRKEh0fcaYCga6oDgS6oK4QqqLjwqxM5qrCtnYhwQC19v9tUme5xwnocQG6HYBJZcx1xQng3zUd4rD2vTlr4z3B6Kh5vka1" +
            "rKQyGY5oN2nj2zvUjhGhiaQymYFsN8rt7WOqbCTVKsJ3Z/CHpwQwx7USmBLWZ9h7yXLNqfBg2V9huQZ5Ve9fOtxoJr0jtnHiDLdm0onwoyeQvX" +
            "NK1bnkRLha8t3CAGzSGONYEXXgUw0/HZWTdiaVYAlSwreTytwwEb4/w+jk/CGWnoMIOhiw5lJaSU2UUGv+tn37mNi0Ka0MUce1f/Br8k2j6qh7" +
            "rJpGFXY52+ECB8MtBpq4SdvPNuxXi/a9d473NCZMMZq5BijhIxKoPtT6NmOtwuGTvo6sD6/x0RF3jIpKXb7RMRfW8aJ2LJtUjBvsOpu19I4aaG" +
            "mP39njdXb7nNN6mUSHiTQEuGGymsCDGe7Nwh8x5b5Dyuv4dHDe44pgYI17LLwMePn8uARqv/oLkrFdYtl3wBHHqpcN4X7LCNfcFUJDpC+KzrkD" +
            "HRl3oCPjDnRkFF2p8ns1LNQlmf60vIxmOGl1Gc2AzcAOZL4XMprhaVEzPG1qhqdVyIOc55XrZdQ8e4pn5uwTOL8zejXAOAJPRkavBhkIqz+jV3" +
            "sYCN6MjF69xkBwFGVu6k7FvzlRNnA+h8d5pNJt73b0U3tDphKRMD7ZIsEEt+7fwetfxARLfREW3hARUSnB6hGWppSUyShHPIMsJWUXLcNodMQE" +
            "DcIEEU8WoockIgPerxw7maHE9QcAZOhHexa+EB4mKcswQgmUsuFkLEL+wUc71M3B0QMc3Tyab55BUpbEgoJ0LK6EAaWh/t/Y7Yf2w4vW4b4T+h" +
            "z63s0IE0nKTR52JUku3MkiKTehRyxfRvRLrfRpVdUJOOG9b0KkSWolrZfHyjUX2RsektQqE6o08YSLVkmtEg4p2s8haBO0W2IktUpF4jEPsZ0k" +
            "bQJZJ69YzMRwpbNJhsmGHoBGrVBVq4hemRBnoNSoQWIGQztTNElchYafBblDxl6nyBncp6JmjshANpGcdN2p8eBlZG9+EGeeuO3rKUQcVDRoTT" +
            "suy86tdicQLYc6fZxjMXnyO8sD9FzpliGV2pp5RWdbrvbXED7+bJ0+oN5wmSRvOEFx0xX3NiG5gN6upJDqJo6h9tNv1h7Eb2RhOltzK3ieRMhd" +
            "4XHhorwaUctChIXpi16ikNlZJ9Dy/SLFP5hoai0d4uUn34lvZsPcvXd0RGVdNkYVPPt0zgngxzSIvhHvhRMV6yuN8Sa4yMp6+QJM2jncs+L4cx" +
            "+LQgRmndPLQxd9cg6xznTJyX+maybLEwmsuc8ACgvOnSfttJ/zte8f05yvfT9dGXEsmayiO07BrKLzoSKpEuC2P9uiMig7Hq2VWMTRPp2DrH9A" +
            "9perZZOhhWmeHddDE8lJWK74+SwlMnWYKATZcZ2ESf3hS71hmMha2mUtaqZSgegPXcYEJMLCmj4VkiKrTbgmTlcg3wIuX+2Snurq78ha+Ns+fU" +
            "6gP5B174O9+Q4A4wa7SwWASQJPTj2NGhhT9Lamh/FGDTwIDuFgWRBykOBCCAw12dsYZZvLPnuI737C90k3Ie1ceNEG6KE7Dz8TkQDCj/abR9PU" +
            "rQKqwUAiEvQgQUGghiOYmDQz229cDhgqqMY09JdjfnD42U3DFod73RBNyXXTcMrhXjc0leumAZXDvW5oJNdNQyqHe92gheS6r3EQtJBcN412HO" +
            "51Q7gq1/0TByFclev+mYMQrsp1/8JBCFflun/lIATRciQciZcWCBMBziKJqOUCnEUSUcsFgrwsYZFDBCCxS7y0QLglsUu8tEB4JbFLvLRAOCWx" +
            "S7y0QPgksUu8tEC4/IUBhMdfGUA47JD97fPAEMz3cgV9HhnAOMa6CydMVgfn9dcARmx+37Z2dr2fwFeAU8egqOP1Ej1ecoOCRwclKhQod85Qzp" +
            "0zlFN1eDcGCTM0Vx5Rh9i1QlK6PKLmGUw+KI+owwwGfXmwG1nP77RnF2HaBwMMgEkfDDIAZnmwhwEwy4PXGACzPAQvAGzj5Xutw83WEcmx6GZK" +
            "IVSZDzAAqswHGQBVilvCUGX+GgOgynyYAW7fWz4s+7HiqC2OKzUWmIXMTb9HkOvHJWUKWavgLlJDkFtI8zu4fFcdU8TB5eAKi/ccUOujJURf/h" +
            "DiRP1+9sxoedIfeRi94oMfbdSK9L4efYLGROOIekrJpYV6kdqWM2/cGc4VVanLqL16QHNmK+78xSoCn+3fsHiqypjLb5SgmN8czG+AqRpllEzK" +
            "CRG4rhpj7BYKUcREgKBqjiNrA/xWNYh2Pz6gQ1trVMH9xjLTqKqg+ZIdtVEjgRJu8Df0mwM2TN7N2RN3N+vKFLl3Zm3csx7Ptc7PW4vAYF3YiT" +
            "xkXXebkroCUYwdnt9ehqv7iST8Lk7Q9EN6k5jrMLpaJOobRQutTVfroDXjbZglXTUg8GefPrRegFtPh1Ry+sqEifTJUp2bFzx0p09yFIUNRKfK" +
            "UP+oac7NE0Nr6EUVWc/fWCcgtwyjgy5rjgpVDLYD6OmGqVTrkEAttBjXYWR6j3Fzss6DNYSVRq2o6qZSrplTqHm0aL/cw3vP6GiRZG0aU5kkWZ" +
            "FTJCtySrmp1MYg8uRKm+RIcC4JvFj7U6qiI3wM4/snyakMRSSerH5229455WlQie+G5EPgDqZ3yLiaExr8L8SzmqffhNQLjXwvXytUJK6p9toM" +
            "VQ9CfJ/twlsxFJ5QkXU5bX2BZRjiFz1zSqVcYrc86dKDVBg5i/AKyE6xcHqzyHrxFz49cTADDGM/2rXmSBaIVjPMQkYzCiRvGO89a//7VGQPh3" +
            "We7CE000gowfPFaBReDIzwnEfgM2tj09pY938Mgc8Lki7Aw58RxVRYLgKJSCulgqQWb2g1lmPdPF+0lnbtPdjh0esZFhhmj3cw3/Yyu6xMS9AI" +
            "sVPiYNlTogYxypI7LuwJDOvxGCS+0IsS06etO+fgWuZ2ZVTX+7ljkjxNBMFGHvIlJlws51kBdBvFBmWGxR9W7fdvm0efCDbLy4oQKmRU+lETLK" +
            "LaXv2HqrixckVNQbSTvi6EV0iwtGoSRdIflNVVlViaq5+EihyPSCQM6Vro8X6CEis5niRBT5Y2znc6iVRWwFkDsxPPyK3Dz3iZVJmRC5DRDfcZ" +
            "4ppWIkTi4Wjd28dvYKDjg96LFrSiQoTfraTRcTx3ICrt63A5ry/yvesjfXB9hCzl0IjBirALmafP8f6xp4gkEvZZEeIQEUVyHfL5I04UkT4nRV" +
            "Yf1arpUUOKsHiiKHKw7CmSifHDge+ARC7a3SE+BypKIhcNdCCBwpLIRYMdSKC+JHLRng4kUGYSuei1DiRQbRK5mEcrFFGgRC7V3SFGSPlLBTqQ" +
            "KH+pYAcS5S/V04FE+Utd60Ai/NXKZlmplP+EJIDDT3juOTjE7oN/wOXlJyEupYTEPXCA2BfzWxSVTPM4j+NNT6Z5uMdxqifTPOrj+NaTyu++SM" +
            "9klqntThAFtHZ3/AWO5ubxUxbaaVTMcqJkILz9jAYy+mF/gleLRijIbQ+CEBoI2ex4ZdHeISVKEGCZ22qvbQstJx1PEXegffsY7igLCZ5Ra+S2" +
            "Cr6Yoe910YscpYJ3t1AXPdtWZLc4nm9vadg4ntJk44jSv5G4BVV4RdxCSsfIZRt8/qq1f2iRQKakQrC0XBsriGtH1C4Xl48ktZrRTOZ1tB99Zd" +
            "5SlXBrP14jAPEctvYfW+9Zmo40yfQa8GU5ygpxjylFGIr26heqF8mmUlEL5ABqXc7CgcWPoWyfP+0+q9xQtdHRTtLq62H79QtXkehknRaBYdrY" +
            "dOiqrvddcWOpug6XOh0PF71exV0pVxwpWqM4XtJu1q40s/MWbxA3GPffgdlfo7e8iSIlsl4GamWTLEDy3gtZgwOQ1cUc2QMGzApkvVDBRXNfBs" +
            "gOsx/t4sM3eAaqyQW4O4Bkr+YC3B1AsldzLPnxcI9kr+bghT4KEjM7wN0BJHs1F+DuAJK9mgtwd0CAGNtB7gEIEg9AkHsAgsQDEOROiiD1AHCu" +
            "goQrWdUnNNSaI4+R8rhvjoQHiLAT+fe5YYZhN9dyqk4UI4YVK5zgc50u8RQRPptuHe47zWgVU4JATWv6jkAO5iLskSKwBOfu0mU9mIsK7IYLm2" +
            "JYkIkcmyfOB5oB4fK/gqUX80RnpBi10biJxmwZVzRQZwYftTi4JRXoRsTXEggg4mQJBBHxrgR6EJnqkOtlVaI6MwWX6cVJhI9nGCmF8Nlt4aIN" +
            "paP8ibujDUeRTmf5lRHuaQwVizQdzLkdwJkL1cYiQ06DJN2+NhbJOyiScV8biww7KJZ0n5GL/FOaEse+zsjFvAfPqsjIxWEPHurpzUSQ9eSDff" +
            "tYxMV6M1GO4p3qzaQYSsjocOBXePAFQnwkVTxIHs+FEB9JFQ8GOEgT1oMcJKniwR4OklTx4DUOklTx4I8cJKniwZ84SFLFgz9zkKSKB3/hIEkV" +
            "D3KugoSrHs5VD+Gqh3PVQ7jq4Vz1EK5CNWStf4LkQWJzhSNlgyBOLsXYhPuuI2vrZfuf+wBUS0hET+E2aIa+WEMuJFEPcuQaMwegY5EfGQDdiv" +
            "BHPqFTkZ8ZAF2KQG6pxxihudadDRVCS/m/YDnYzi1MnvAsbmLyNGZRLdXjGDbFb9G4ZjwywNPoRBIy89mIvc8JKZ5qxTe6k/Dbyzf7LRcy6r/1" +
            "BOHbbhZ3JZnxZcMMMJgkx1emXInAU7WMDpdWdvES2QQEkWeIPEcMMwQs+2gwy+ykYPvpHEuwpS9nOXm4NX0M7g0Iw4mYTGH2nZNKO4noY3/QMs" +
            "s+EupmDNhaJxTgZx2YiQEj68BFTIGUVl8WcawKZvO62/0eq05yFHfVkwRSQBKriCW5kqvA/CLw/gWNEsTDFYZy33SAFE2GpZHzJ6D2AGHSl6U5" +
            "5cvSdCWZQh/6QnxRuPrQRzSAvW/45IOTmAkvBIOSJTZTH7lyR0qJVJE+UBgZjjvZ+uQYz+gk4+q6vcy8HE6mp6pUrtxxhgwi0rJIIkkEw2H4I0" +
            "xhQNTIP2JeE8FwAv6wxwPpZWfvJecEuQhGVHXBAeCgKjfaqRNM7pqX9hJESaIWZceaqKg/AucF0/sHmA2A595BNjKZQv44NXkiIgn5pafwZJ6J" +
            "kkmE7x+3Zxadi9PKZIRf0HLlaskmCcezdXXyiC6nVKMqk/esXO4xRzUPsXuS8ICFyO8JsYuSgBSpQLEIuwArmnRecnTlJvXzUjyFIOPJHfFkjn" +
            "jyRjxZIyGWb+FehZkInJBCsDmHJBDyfgJJconAGekjkHSXOMe+emGvQzgrI95QdZ/+GfFOqjj9M4pz8dzDHexz0gH3Vs/AVhdYvtszGrzaI/Pn" +
            "XKmvwP0ORUYzIq6usvydiKuTLIcn4uoe6Zherio6qNSnNJ4Gfo9udpW9m8EBBpOb8dQv4rrsnh41mKImTDwJIr4slYNlVkDOxZV8kFK5AZr7PL" +
            "74i9lI4Ea88saI1KixXAmS+QDPX/qEJjxiTR+a5FtNTuSop+iLvfDe/pdEgwi/ciYRhj9OekUmARtWziScjxXWhm/OZGVUNaeQuG8gs4eN5lap" +
            "a0yGOSX7yD2nMsypwPI5ZZtPbLusOOFFiywgHuKnse94z4IGyihX9NAsKKJuohP4jonqSLsdVYCsTwWggX/e3NWFn4WV7yY6zeV4/T5lgdB4Kz" +
            "59gcb5o74PXc3lRHPMX+s0J/PueU2a7BALj8PmyOYZANsiO8yAYceGZfYrINyOfxqo90TtidJHr3HykRqI5Vnsmh5Ywiwb6BQNoHG3XIjbcK71" +
            "lgszpDjNcyTZy+u7h4G6OkQwQr6xyRWLLOybC4ep9edENMl9DnGNY7AfWZ/h5hz8dgIVgzeSMvwUL8jkQx2Mt3yWIUXLnRS/TnpfXrkp8Yvjwt" +
            "GuZLSbkDNO/MYiN5A/OyJOY2WsCPs4FHfO8BHIUjx7SCeyCHGarZfwJr6JijWNiKO1f6iIKbovbRY14q1vXR5TS6Co1UYRLPjLl7SDgCiPofYM" +
            "M6OLdf6/fXDvoWI9CfeKCJ79LyVMVII3C6zHB/jDqjgESxp5LttaZMmf4sAsaaQGF8mpx53fWOJ31WjDJUi7X31Dc/tpdFG8A6mrfzjaIrwHSJ" +
            "4BJA/F8QeOwM6uat7bJ1yyk5crnVsbBOO/e/C7Uk3USoi+C23dn7W3QUOpeJ96qVTKiOYOQhhv+6NTTiveQO1Ht+g9qqpSY49yAuBOJK+qxm/k" +
            "zW5wy5PDyLo/S1cb7Fi+QKsGj60C0KgQ3+Xnx9SLVoPMWe6JrEFsgj5/baIa5A5mVB2yUPaXaW0ih7BmIPvkM62/1qiKRDehJdHHRKkJWNcjzM" +
            "copquuw7QyxyOf0DqEp/7dh0dzTfSH/5QlT685r1KqRrkET85Yewt0ynWQSuTIJXHPIo+AViGuCul7JP45hiBRkrzayB7NMmvI/neNcmqYOlw/" +
            "FyEd8f6iOw7qkYWmfgOJfARzqu4k/pDXJwVEXqh0PQnlSJ0Jb2LF/wGDEb4yLmgAAA==";

    static { load(DATA, moduleMap, tokenMap); }

    private static void load(String data, Map<String,String> first, Map<String,String> second) {
        try {
            InputStream raw = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)));
            BufferedReader reader = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null;) {
                String[] parts = line.split("\\t", 3);
                (parts[0].charAt(0) == 't' ? second : first).put(parts[1], parts[2]);
            }
            reader.close();
        } catch (IOException impossible) { throw new ExceptionInInitializerError(impossible); }
    }

    private FieldNameLocalizer() {}

    public static String toZhCn(String fieldName) {
        if (fieldName == null || fieldName.length() == 0) return "";
        int index = fieldName.indexOf('.');
        if (index <= 0 || index >= fieldName.length() - 1) return translateToken(fieldName);
        String module = fieldName.substring(0, index);
        String token = fieldName.substring(index + 1);
        String moduleZh = moduleMap.get(module);
        if (moduleZh == null) {
            int underscore = module.lastIndexOf('_');
            if (underscore > 0 && underscore < module.length() - 1 && digits(module.substring(underscore + 1))) {
                String base = module.substring(0, underscore), instance = module.substring(underscore + 1);
                if (moduleMap.containsKey(base)) moduleZh = moduleMap.get(base) + instance;
                else if (moduleMap.containsKey(base + "_0")) {
                    moduleZh = moduleMap.get(base + "_0");
                    if (moduleZh.endsWith("0")) moduleZh = moduleZh.substring(0, moduleZh.length() - 1);
                    moduleZh += instance;
                } else moduleZh = translateToken(base) + instance;
            } else moduleZh = translateToken(module);
        }
        return moduleZh + "." + translateToken(token);
    }

    private static String translateToken(String token) {
        String direct = tokenMap.get(token);
        if (direct != null) return direct;
        StringBuilder result = new StringBuilder();
        for (String part : token.split("_")) {
            if (part.length() == 0) continue;
            if (result.length() > 0) result.append('_');
            result.append(tokenMap.containsKey(part) ? tokenMap.get(part) : part);
        }
        return result.toString();
    }

    private static boolean digits(String value) {
        if (value.isEmpty()) return false;
        for (int i=0;i<value.length();i++) if (!Character.isDigit(value.charAt(i))) return false;
        return true;
    }
}
