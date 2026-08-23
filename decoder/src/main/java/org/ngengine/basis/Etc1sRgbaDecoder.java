package org.ngengine.basis;

/**
 * ETC1S block stream decoder for RGBA8, ETC1, ETC2/EAC, and BCn output.
 */
final class Etc1sRgbaDecoder {
    private static final int ENDPOINT_PRED_REPEAT_LAST_SYMBOL = 256;
    private static final int ENDPOINT_PRED_MIN_REPEAT_COUNT = 3;
    private static final int ENDPOINT_PRED_COUNT_VLC_BITS = 4;
    private static final int SELECTOR_HISTORY_BUF_RLE_COUNT_THRESH = 3;
    private static final int SELECTOR_HISTORY_BUF_RLE_COUNT_TOTAL = 64;
    private static final int[][] EAC_MODIFIER_TABLE = {
        {-3, -6, -9, -15, 2, 5, 8, 14},
        {-3, -7, -10, -13, 2, 6, 9, 12},
        {-2, -5, -8, -13, 1, 4, 7, 12},
        {-2, -4, -6, -13, 1, 3, 5, 12},
        {-3, -6, -8, -12, 2, 5, 7, 11},
        {-3, -7, -9, -11, 2, 6, 8, 10},
        {-4, -7, -8, -11, 3, 6, 7, 10},
        {-3, -5, -8, -11, 2, 4, 7, 10},
        {-2, -6, -8, -10, 1, 5, 7, 9},
        {-2, -5, -8, -10, 1, 4, 7, 9},
        {-2, -4, -8, -10, 1, 3, 7, 9},
        {-2, -5, -7, -10, 1, 4, 6, 9},
        {-3, -4, -7, -10, 2, 3, 6, 9},
        {-1, -2, -3, -10, 0, 1, 2, 9},
        {-4, -6, -8, -9, 3, 5, 7, 8},
        {-3, -5, -7, -9, 2, 4, 6, 8}
    };
    private static final int[][] EAC_SELECTOR_RANGES = {
        {0, 3},
        {1, 3},
        {0, 2},
        {1, 2}
    };
    private static final int[][] DXT5_ALPHA_SELECTOR_RANGES = {
        {0, 3},
        {1, 3},
        {0, 2},
        {1, 2}
    };
    private static final int[][] ETC1_INTENSITY_TABLES = {
        {-8, -2, 2, 8},
        {-17, -5, 5, 17},
        {-29, -9, 9, 29},
        {-42, -13, 13, 42},
        {-60, -18, 18, 60},
        {-80, -24, 24, 80},
        {-106, -33, 33, 106},
        {-183, -47, 47, 183}
    };
    private static final byte[] OPAQUE_ETC2_EAC_A8_BLOCK = {
        (byte) 0xFF, 0x1D, (byte) 0x92, 0x49, 0x24, (byte) 0x92, 0x49, 0x24
    };
    private static final byte[] OPAQUE_DXT5_ALPHA_BLOCK = {
        (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0, 0, 0
    };
    private static final EacConversion[][] EAC_A8_CONVERSION_CACHE =
            new EacConversion[32 * ETC1_INTENSITY_TABLES.length][EAC_SELECTOR_RANGES.length];
    private static final EacConversion[][] EAC_R11_CONVERSION_CACHE =
            new EacConversion[32 * ETC1_INTENSITY_TABLES.length][EAC_SELECTOR_RANGES.length];
    private static final Dxt5AlphaConversion[][] DXT5_ALPHA_CONVERSION_CACHE =
            new Dxt5AlphaConversion[32 * ETC1_INTENSITY_TABLES.length][DXT5_ALPHA_SELECTOR_RANGES.length];

    private static final int[] DXT5_ALPHA_CONVERSION_TABLE = {
            25755656, 25690120, 589826, 524290, 46534662, 21497360, 6294016, 525834,
            86967580, 21499416, 6296072, 527890, 86969636, 21501472, 6298128, 529946,
            86971949, 21503785, 6300441, 532259, 86974005, 21505841, 6302497, 534315,
            86976061, 21507897, 6304553, 536371, 86978117, 21509953, 6306609, 538427,
            86980430, 21512266, 6308922, 540740, 86982486, 21514322, 6310978, 542796,
            86984542, 21516378, 6313034, 544852, 86986598, 21518434, 6315090, 546908,
            86988911, 21520747, 6317403, 549221, 86990967, 21522803, 6319459, 551277,
            86993023, 21524859, 6321515, 553333, 86995079, 21526915, 6323571, 555389,
            86997392, 21529228, 6325884, 557702, 86999448, 21531284, 6327940, 559758,
            87001504, 21533340, 6329996, 561814, 87003560, 21535396, 6332052, 563870,
            87005873, 21537709, 6334365, 566183, 87007929, 21539765, 6336421, 568239,
            87009985, 21541821, 6338477, 570295, 87012041, 21543877, 6340533, 572351,
            87014354, 21546190, 6342846, 574664, 87016410, 21548246, 6344902, 576720,
            87018466, 21550302, 6346958, 578776, 87020522, 21552358, 6349014, 580832,
            87022835, 21554671, 6351327, 583145, 87024891, 21556727, 6353383, 585201,
            241236463, 239139317, 6355439, 587257, 264830455, 589311, 29949431, 589311,
            37097733, 36704517, 589829, 524293, 20512793, 21496601, 3211277, 525069,
            87097383, 21498657, 4592907, 527125, 87099183, 21500713, 2164509, 529181,
            15665970, 21503026, 2166822, 531494, 158797148, 21505082, 2168878, 533550,
            158799204, 21507138, 2170934, 535606, 82118486, 21509194, 2172990, 537662,
            82120799, 21511507, 2175303, 539975, 82122855, 21513563, 2177359, 542031,
            82124911, 21515619, 2179415, 544087, 82126967, 21517675, 2181471, 546143,
            82129280, 21519988, 2183784, 548456, 82131336, 21522044, 2185840, 550512,
            82133392, 21524100, 2187896, 552568, 82135448, 21526156, 2189952, 554624,
            82137761, 21528469, 2192265, 556937, 82139817, 21530525, 2194321, 558993,
            82141873, 21532581, 2196377, 561049, 82143929, 21534637, 2198433, 563105,
            82146242, 21536950, 2200746, 565418, 82148298, 21539006, 2202802, 567474,
            82150354, 21541062, 2204858, 569530, 82152410, 21543118, 2206914, 571586,
            82154723, 21545431, 2209227, 573899, 82156779, 21547487, 2211283, 575955,
            82158835, 21549543, 2213339, 578011, 56881079, 21551599, 2215395, 580067,
            87152382, 21553912, 2217708, 582380, 241235166, 239138026, 2219764, 584436,
            241237222, 239140082, 2221820, 586492, 264829678, 588543, 29948654, 588543,
            37100809, 36707593, 589833, 524297, 37102865, 36709649, 589841, 524305,
            20512813, 20447277, 3211289, 526105, 180764430, 51393797, 4595983, 528161,
            87098951, 87032904, 2163754, 530474, 15663942, 15204934, 2165810, 532530,
            186278400, 15206990, 2167866, 534586, 87104353, 15209046, 2169922, 536642,
            56843776, 15211359, 2172235, 538955, 56845832, 15213415, 2174291, 541011,
            56848396, 15215471, 2176347, 543067, 82117259, 15217527, 2178403, 545123,
            82120084, 15219840, 2180716, 547436, 82122140, 15221896, 2182772, 549492,
            82124196, 15223952, 2184828, 551548, 82126252, 15226008, 2186884, 553604,
            82128565, 15228321, 2189197, 555917, 82130621, 15230377, 2191253, 557973,
            82132677, 15232433, 2193309, 560029, 82134733, 15234489, 2195365, 562085,
            82137046, 15236802, 2197678, 564398, 82139102, 15238858, 2199734, 566454,
            82141158, 15240914, 2201790, 568510, 82143214, 15242970, 2203846, 570566,
            82145527, 15245283, 2206159, 572879, 82147583, 15247339, 2208215, 574935,
            56882066, 15249395, 2210271, 576991, 241362872, 51444683, 2212327, 579047,
            241365185, 239136990, 2214640, 581360, 11129599, 239139046, 2216696, 583416,
            264826586, 585471, 29945562, 585471, 264828642, 587519, 29947618, 587519,
            37104141, 36710925, 589837, 524301, 21561394, 21495858, 589845, 524309,
            37108253, 88605251, 4594947, 525085, 180768522, 88607564, 4597003, 527141,
            51792647, 51399431, 4599316, 529454, 51794703, 87032161, 4601372, 531510,
            87099237, 87034217, 2555966, 533566, 15663459, 15205219, 6440705, 535622,
            15666027, 15207532, 6443018, 537935, 15668083, 15209588, 6445074, 539991,
            15670139, 15211644, 6447130, 542047, 15672195, 15213700, 6449186, 544103,
            15674508, 15216013, 6451499, 546416, 15676564, 15218069, 6453555, 548472,
            15678620, 15220125, 6455611, 550528, 15680676, 15222181, 6457667, 552584,
            15682989, 15224494, 6459980, 554897, 15685045, 15226550, 6462036, 556953,
            15687101, 15228606, 6464092, 559009, 15689157, 15230662, 6466148, 561065,
            15691470, 15232975, 6468461, 563378, 15693526, 15235031, 6470517, 565434,
            15695582, 15237087, 6472573, 567490, 15697638, 15239143, 6474629, 569546,
            15699951, 15241456, 6476942, 571859, 15702007, 51443892, 6478998, 573915,
            241362079, 239133641, 6481054, 575971, 257489333, 239135697, 6483110, 578027,
            11124223, 239138010, 6485423, 580340, 264823493, 239140066, 6487479, 582396,
            264825549, 584447, 29944525, 584447, 264827605, 586495, 29946581, 586495,
            37108754, 36715538, 589842, 524306, 37110810, 36717594, 589850, 524314,
            37112866, 36719650, 589858, 524322, 180774917, 88606050, 3735594, 525866,
            20512860, 20447581, 4600591, 528179, 51799299, 51406083, 5782272, 530235,
            51800846, 51408139, 4604703, 532291, 51803411, 51410195, 4606759, 534347,
            51805724, 51412508, 2164051, 536660, 15663236, 51414564, 2166107, 538716,
            15664270, 51416620, 2168163, 540772, 15666326, 51418676, 2170219, 542828,
            15668639, 51420989, 2172532, 545141, 15670695, 51423045, 2174588, 547197,
            15672751, 51425101, 2176644, 549253, 15674807, 51427157, 2178700, 551309,
            15677120, 51429470, 2181013, 553622, 15679176, 51431526, 2183069, 555678,
            15681232, 51433582, 2185125, 557734, 15683288, 51435638, 2187181, 559790,
            15685601, 51437951, 2189494, 562103, 15687657, 51440007, 2191550, 564159,
            15689713, 51442063, 2193606, 566215, 241356911, 51444119, 2195662, 568271,
            241359224, 239130804, 2197975, 570584, 241361280, 239132860, 2200031, 572640,
            257490331, 239134916, 2202087, 574696, 241365392, 239136972, 2204143, 576752,
            241367705, 239139285, 2206456, 579065, 264822195, 581119, 29941171, 581119,
            264824251, 583167, 29943227, 583167, 264826307, 585215, 29945283, 585215,
            37113880, 36720664, 589848, 524312, 37115936, 36722720, 589856, 524320,
            37117992, 36724776, 589864, 524328, 37120048, 36726832, 589872, 524336,
            180783625, 88606594, 4602121, 526649, 20512887, 20447352, 4604177, 528705,
            51412992, 20448896, 4606233, 530761, 51808518, 51415045, 4608289, 532817,
            180792106, 51417358, 4610602, 535130, 180794162, 51419414, 4612658, 537186,
            180796218, 51421470, 4614714, 539242, 87102143, 51423526, 2165104, 541298,
            15663280, 51425839, 2167417, 543611, 15663547, 51427895, 2169473, 545667,
            15665859, 51429951, 2171529, 547723, 15667915, 51432007, 2173585, 549779,
            15670228, 51434320, 2175898, 552092, 15672284, 51436376, 2177954, 554148,
            15674340, 51438432, 2180010, 556204, 15676396, 51440488, 2182066, 558260,
            15678709, 51442801, 2184379, 560573, 241222235, 239125909, 2186435, 562629,
            241224291, 239127965, 2188491, 564685, 241226347, 239130021, 2190547, 566741,
            257489271, 239132334, 2192860, 569054, 56164223, 239134390, 2194916, 571110,
            11110399, 239136446, 2196972, 573166, 241234828, 239138502, 2199028, 575222,
            264818583, 577535, 29937559, 577535, 264820639, 579583, 29939615, 579583,
            264822695, 581631, 29941671, 581631, 264824751, 583679, 29943727, 583679,
            37120545, 36727329, 589857, 524321, 37122601, 36729385, 589865, 524329,
            37124657, 36731441, 589873, 524337, 37126713, 36733497, 589881, 524345,
            37129026, 36735810, 589890, 524354, 37131082, 88606634, 4606472, 526410,
            20512920, 88608690, 5263872, 528466, 20512930, 88610746, 4610584, 530522,
            51424000, 88613059, 4612897, 532835, 51819270, 88615115, 4614953, 534891,
            51821327, 88617171, 2687091, 536947, 46581565, 88619227, 4619065, 539003,
            46583878, 88621540, 4621378, 541316, 46585934, 88623596, 4623434, 543372,
            46587990, 88625652, 2164625, 545428, 15272158, 88627708, 2166681, 547484,
            15663339, 21521905, 2557606, 549797, 241216032, 21523961, 6467328, 551853,
            241218088, 239121779, 6469384, 553909, 241220144, 239123835, 6471440, 555965,
            241222457, 239126148, 6473753, 558278, 257487683, 239128204, 6475809, 560334,
            257489740, 239130260, 6477865, 562390, 56164182, 239132316, 6479921, 564446,
            11099647, 239134629, 6482234, 566759, 241232994, 239136685, 6484290, 568815,
            264811884, 239138741, 6486346, 570871, 264813940, 572927, 29932916, 572927,
            264816253, 575231, 29935229, 575231, 264818309, 577279, 29937285, 577279,
            264820365, 579327, 29939341, 579327, 264822421, 581375, 29941397, 581375,
            37140271, 36747055, 589871, 524335, 37142327, 36749111, 589879, 524343,
            37144383, 36751167, 589887, 524351, 37146439, 36753223, 589895, 524359,
            37148752, 36755536, 589904, 524368, 37150808, 36757592, 589912, 524376,
            46590211, 46196995, 4612098, 524896, 46592267, 46199051, 4614154, 526952,
            46594580, 46201364, 4616467, 529265, 239499547, 239106331, 4618523, 531321,
            239501603, 239108387, 4620579, 533377, 239503659, 239110443, 4622635, 535433,
            239505972, 239112756, 4624948, 537746, 239508028, 239114812, 4627004, 539802,
            239510084, 239116868, 4629060, 541858, 239512140, 239118924, 4631116, 543914,
            239514453, 239121237, 4633429, 546227, 239516509, 239123293, 4635485, 548283,
            239518565, 239125349, 4637541, 550339, 239520621, 239127405, 4639597, 552395,
            239522934, 239129718, 4641910, 554708, 239524990, 239131774, 4643966, 556764,
            239527046, 239133830, 4646022, 558820, 241232901, 239135886, 6351877, 560876,
            241235214, 239138199, 6354190, 563189, 264806167, 239140255, 29925143, 565245,
            264808223, 567295, 29927199, 567295, 264810279, 569343, 29929255, 569343,
            264812592, 571647, 29931568, 571647, 264814648, 573695, 29933624, 573695,
            264816704, 575743, 29935680, 575743, 264818760, 577791, 29937736, 577791
    };

    private Etc1sRgbaDecoder() {
    }

    static byte[] decode(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decode(imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    static byte[] decode(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBlockStream(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                OutputMode.RGBA8,
                videoState,
                levelIndex,
                alphaSlice);
    }

    static byte[] decodeEtc1Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeEtc1Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    static byte[] decodeEtc1Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBlockStream(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                OutputMode.ETC1,
                videoState,
                levelIndex,
                alphaSlice);
    }

    static byte[] decodeEtc2RgbaBlocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeEtc2RgbaBlocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    static byte[] decodeEtc2RgbaBlocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBlockStream(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                OutputMode.ETC2_RGBA,
                videoState,
                levelIndex,
                alphaSlice);
    }

    static byte[] decodeEtc2RgbaBlocks(
            byte[] rgbData,
            int rgbOffset,
            int rgbLength,
            byte[] alphaData,
            int alphaOffset,
            int alphaLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeEtc2RgbaBlocks(
                rgbData, rgbOffset, rgbLength, alphaData, alphaOffset, alphaLength,
                width, height, tables, palettes, null, 0);
    }

    static byte[] decodeEtc2RgbaBlocks(
            byte[] rgbData,
            int rgbOffset,
            int rgbLength,
            byte[] alphaData,
            int alphaOffset,
            int alphaLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        byte[] colorBlocks = decodeEtc1Blocks(
                rgbData, rgbOffset, rgbLength, width, height, tables, palettes,
                videoState, levelIndex, false);
        byte[] alphaBlocks = decodeEacA8Blocks(
                alphaData,
                alphaOffset,
                alphaLength,
                width,
                height,
                tables,
                palettes,
                videoState,
                levelIndex,
                true);
        byte[] rgbaBlocks = new byte[Math.addExact(colorBlocks.length, alphaBlocks.length)];
        int blockCount = colorBlocks.length / 8;
        for (int i = 0; i < blockCount; i++) {
            System.arraycopy(alphaBlocks, i * 8, rgbaBlocks, i * 16, 8);
            System.arraycopy(colorBlocks, i * 8, rgbaBlocks, i * 16 + 8, 8);
        }
        return rgbaBlocks;
    }

    static byte[] decodeEtc2EacR11Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeEtc2EacR11Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    static byte[] decodeEtc2EacR11Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBlockStream(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                OutputMode.ETC2_EAC_R11,
                videoState,
                levelIndex,
                alphaSlice);
    }

    static byte[] decodeEtc2EacRg11Blocks(
            byte[] channelZeroData,
            int channelZeroOffset,
            int channelZeroLength,
            byte[] channelOneData,
            int channelOneOffset,
            int channelOneLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeEtc2EacRg11Blocks(
                channelZeroData,
                channelZeroOffset,
                channelZeroLength,
                channelOneData,
                channelOneOffset,
                channelOneLength,
                width,
                height,
                tables,
                palettes,
                null,
                0);
    }

    static byte[] decodeEtc2EacRg11Blocks(
            byte[] channelZeroData,
            int channelZeroOffset,
            int channelZeroLength,
            byte[] channelOneData,
            int channelOneOffset,
            int channelOneLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        byte[] channelZero = decodeEtc2EacR11Blocks(
                channelZeroData,
                channelZeroOffset,
                channelZeroLength,
                width,
                height,
                tables,
                palettes,
                videoState,
                levelIndex,
                false);
        byte[] channelOne = decodeEtc2EacR11Blocks(
                channelOneData,
                channelOneOffset,
                channelOneLength,
                width,
                height,
                tables,
                palettes,
                videoState,
                levelIndex,
                true);
        return interleaveEacRg11Blocks(channelZero, channelOne);
    }

    static byte[] decodeEtc2EacRg11Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeEtc2EacRg11Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0);
    }

    static byte[] decodeEtc2EacRg11Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        byte[] channelZero = decodeEtc2EacR11Blocks(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                videoState,
                levelIndex,
                false);
        byte[] channelOne = opaqueEacR11Blocks(width, height);
        return interleaveEacRg11Blocks(channelZero, channelOne);
    }

    private static byte[] interleaveEacRg11Blocks(byte[] channelZero, byte[] channelOne) {
        if (channelZero.length != channelOne.length || channelZero.length % 8 != 0) {
            throw new BasisDecodeException("ETC2 EAC RG11 channel block sizes do not match");
        }
        byte[] rgBlocks = new byte[Math.addExact(channelZero.length, channelOne.length)];
        int blockCount = channelZero.length / 8;
        for (int i = 0; i < blockCount; i++) {
            System.arraycopy(channelZero, i * 8, rgBlocks, i * 16, 8);
            System.arraycopy(channelOne, i * 8, rgBlocks, i * 16 + 8, 8);
        }
        return rgBlocks;
    }

    private static byte[] opaqueEacR11Blocks(int width, int height) {
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        int blockCount = Math.multiplyExact(blocksX, blocksY);
        byte[] blocks = new byte[Math.multiplyExact(blockCount, 8)];
        for (int i = 0; i < blockCount; i++) {
            System.arraycopy(OPAQUE_ETC2_EAC_A8_BLOCK, 0, blocks, i * 8, OPAQUE_ETC2_EAC_A8_BLOCK.length);
        }
        return blocks;
    }

    static byte[] decodeBc1Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeBc1Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    static byte[] decodeBc1Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBc1Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes,
                true, videoState, levelIndex, alphaSlice);
    }

    private static byte[] decodeBc1Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            boolean useThreeColorBlocks) {
        return decodeBc1Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes,
                useThreeColorBlocks, null, 0, false);
    }

    private static byte[] decodeBc1Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            boolean useThreeColorBlocks,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBlockStream(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                useThreeColorBlocks ? OutputMode.BC1 : OutputMode.BC1_NO_THREECOLOR,
                videoState,
                levelIndex,
                alphaSlice);
    }

    static byte[] decodeBc3Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeBc3Blocks(imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0);
    }

    static byte[] decodeBc3Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        byte[] colorBlocks = decodeBc1Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes,
                false, videoState, levelIndex, false);
        byte[] bc3Blocks = new byte[Math.multiplyExact(colorBlocks.length / 8, 16)];
        int blockCount = colorBlocks.length / 8;
        for (int i = 0; i < blockCount; i++) {
            System.arraycopy(OPAQUE_DXT5_ALPHA_BLOCK, 0, bc3Blocks, i * 16, OPAQUE_DXT5_ALPHA_BLOCK.length);
            System.arraycopy(colorBlocks, i * 8, bc3Blocks, i * 16 + 8, 8);
        }
        return bc3Blocks;
    }

    static byte[] decodeBc7Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeBc7Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    static byte[] decodeBc7Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBlockStream(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                alphaSlice ? OutputMode.BC7_ALPHA : OutputMode.BC7_COLOR,
                videoState,
                levelIndex,
                alphaSlice);
    }

    static byte[] decodeBc4Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeBc4Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    static byte[] decodeBc4Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeDxt5AlphaBlocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes,
                videoState, levelIndex, alphaSlice);
    }

    static byte[] decodeBc5Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeBc5Blocks(imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0);
    }

    static byte[] decodeBc5Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        byte[] channelZero = decodeBc4Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes,
                videoState, levelIndex, false);
        byte[] bc5Blocks = new byte[Math.multiplyExact(channelZero.length / 8, 16)];
        int blockCount = channelZero.length / 8;
        for (int i = 0; i < blockCount; i++) {
            System.arraycopy(channelZero, i * 8, bc5Blocks, i * 16, 8);
            System.arraycopy(
                    OPAQUE_DXT5_ALPHA_BLOCK, 0, bc5Blocks, i * 16 + 8, OPAQUE_DXT5_ALPHA_BLOCK.length);
        }
        return bc5Blocks;
    }

    static byte[] decodeBc5Blocks(
            byte[] channelZeroData,
            int channelZeroOffset,
            int channelZeroLength,
            byte[] channelOneData,
            int channelOneOffset,
            int channelOneLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeBc5Blocks(
                channelZeroData, channelZeroOffset, channelZeroLength,
                channelOneData, channelOneOffset, channelOneLength,
                width, height, tables, palettes, null, 0);
    }

    static byte[] decodeBc5Blocks(
            byte[] channelZeroData,
            int channelZeroOffset,
            int channelZeroLength,
            byte[] channelOneData,
            int channelOneOffset,
            int channelOneLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        byte[] channelZero = decodeBc4Blocks(
                channelZeroData,
                channelZeroOffset,
                channelZeroLength,
                width,
                height,
                tables,
                palettes,
                videoState,
                levelIndex,
                false);
        byte[] channelOne = decodeBc4Blocks(
                channelOneData,
                channelOneOffset,
                channelOneLength,
                width,
                height,
                tables,
                palettes,
                videoState,
                levelIndex,
                true);
        byte[] bc5Blocks = new byte[Math.addExact(channelZero.length, channelOne.length)];
        int blockCount = channelZero.length / 8;
        for (int i = 0; i < blockCount; i++) {
            System.arraycopy(channelZero, i * 8, bc5Blocks, i * 16, 8);
            System.arraycopy(channelOne, i * 8, bc5Blocks, i * 16 + 8, 8);
        }
        return bc5Blocks;
    }

    static byte[] decodeBc3Blocks(
            byte[] rgbData,
            int rgbOffset,
            int rgbLength,
            byte[] alphaData,
            int alphaOffset,
            int alphaLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeBc3Blocks(
                rgbData, rgbOffset, rgbLength, alphaData, alphaOffset, alphaLength,
                width, height, tables, palettes, null, 0);
    }

    static byte[] decodeBc3Blocks(
            byte[] rgbData,
            int rgbOffset,
            int rgbLength,
            byte[] alphaData,
            int alphaOffset,
            int alphaLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        byte[] colorBlocks = decodeBc1Blocks(
                rgbData, rgbOffset, rgbLength, width, height, tables, palettes,
                false, videoState, levelIndex, false);
        byte[] alphaBlocks = decodeDxt5AlphaBlocks(
                alphaData,
                alphaOffset,
                alphaLength,
                width,
                height,
                tables,
                palettes,
                videoState,
                levelIndex,
                true);
        byte[] bc3Blocks = new byte[Math.addExact(colorBlocks.length, alphaBlocks.length)];
        int blockCount = colorBlocks.length / 8;
        for (int i = 0; i < blockCount; i++) {
            System.arraycopy(alphaBlocks, i * 8, bc3Blocks, i * 16, 8);
            System.arraycopy(colorBlocks, i * 8, bc3Blocks, i * 16 + 8, 8);
        }
        return bc3Blocks;
    }

    static byte[] decodeBc7Blocks(
            byte[] rgbData,
            int rgbOffset,
            int rgbLength,
            byte[] alphaData,
            int alphaOffset,
            int alphaLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeBc7Blocks(
                rgbData, rgbOffset, rgbLength, alphaData, alphaOffset, alphaLength,
                width, height, tables, palettes, null, 0);
    }

    static byte[] decodeBc7Blocks(
            byte[] rgbData,
            int rgbOffset,
            int rgbLength,
            byte[] alphaData,
            int alphaOffset,
            int alphaLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        byte[] bc7Blocks = decodeBc7Blocks(
                rgbData, rgbOffset, rgbLength, width, height, tables, palettes,
                videoState, levelIndex, false);
        applyBc7AlphaBlocks(
                bc7Blocks,
                alphaData,
                alphaOffset,
                alphaLength,
                width,
                height,
                tables,
                palettes,
                videoState,
                levelIndex);
        return bc7Blocks;
    }

    private static byte[] decodeEacA8Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeEacA8Blocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    private static byte[] decodeEacA8Blocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBlockStream(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                OutputMode.ETC2_EAC_A8,
                videoState,
                levelIndex,
                alphaSlice);
    }

    private static byte[] decodeDxt5AlphaBlocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes) {
        return decodeDxt5AlphaBlocks(
                imageData, imageOffset, imageLength, width, height, tables, palettes, null, 0, false);
    }

    private static byte[] decodeDxt5AlphaBlocks(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        return decodeBlockStream(
                imageData,
                imageOffset,
                imageLength,
                width,
                height,
                tables,
                palettes,
                OutputMode.DXT5_ALPHA,
                videoState,
                levelIndex,
                alphaSlice);
    }

    private static byte[] decodeBlockStream(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            OutputMode outputMode) {
        return decodeBlockStream(
                imageData, imageOffset, imageLength, width, height, tables, palettes,
                outputMode, null, 0, false);
    }

    private static byte[] decodeBlockStream(
            byte[] imageData,
            int imageOffset,
            int imageLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            OutputMode outputMode,
            VideoState videoState,
            int levelIndex,
            boolean alphaSlice) {
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        int totalBlocks = Math.multiplyExact(blocksX, blocksY);
        int[] previousFrameIndices = videoState == null
                ? null
                : videoState.previousFrameIndices(alphaSlice, levelIndex, totalBlocks);
        byte[] output = allocateOutput(width, height, blocksX, blocksY, outputMode);
        BasisBitReader reader = new BasisBitReader(imageData, imageOffset, imageLength);
        ApproxMoveToFront selectorHistory = new ApproxMoveToFront(tables.getSelectorHistoryBufferSize());
        BlockPredictionState predictionState = new BlockPredictionState(blocksX);

        int currentSelectorRunCount = 0;
        int currentPredictionBits = 0;
        int previousEndpointPredictionSymbol = 0;
        int endpointPredictionRepeatCount = 0;
        int previousEndpointIndex = 0;
        int selectorRleSymbol = palettes.getSelectorCount() + selectorHistory.size();

        for (int blockY = 0; blockY < blocksY; blockY++) {
            int currentRow = blockY & 1;
            for (int blockX = 0; blockX < blocksX; blockX++) {
                if ((blockX & 1) == 0) {
                    if ((blockY & 1) == 0) {
                        if (endpointPredictionRepeatCount != 0) {
                            endpointPredictionRepeatCount--;
                            currentPredictionBits = previousEndpointPredictionSymbol;
                        } else {
                            currentPredictionBits = reader.decodeHuffman(tables.getEndpointPredictionTable());
                            if (currentPredictionBits == ENDPOINT_PRED_REPEAT_LAST_SYMBOL) {
                                endpointPredictionRepeatCount = reader.decodeVariableLengthCode(
                                        ENDPOINT_PRED_COUNT_VLC_BITS) + ENDPOINT_PRED_MIN_REPEAT_COUNT - 1;
                                currentPredictionBits = previousEndpointPredictionSymbol;
                            } else {
                                previousEndpointPredictionSymbol = currentPredictionBits;
                            }
                        }
                        predictionState.predictionBits[currentRow ^ 1][blockX] = currentPredictionBits >>> 4;
                    } else {
                        currentPredictionBits = predictionState.predictionBits[currentRow][blockX];
                    }
                }

                int prediction = currentPredictionBits & 3;
                currentPredictionBits >>>= 2;
                int endpointIndex;
                int selectorIndex = 0;
                boolean copiedFromPreviousFrame = false;
                if (prediction == 0) {
                    if (blockX == 0) {
                        throw new BasisDecodeException("Invalid ETC1S left endpoint predictor at image edge");
                    }
                    endpointIndex = previousEndpointIndex;
                } else if (prediction == 1) {
                    if (blockY == 0) {
                        throw new BasisDecodeException(
                                "Invalid ETC1S upper endpoint predictor at image edge");
                    }
                    endpointIndex = predictionState.endpointIndices[currentRow ^ 1][blockX];
                } else if (prediction == 2) {
                    if (previousFrameIndices != null) {
                        int previousIndex = previousFrameIndices[blockX + blockY * blocksX];
                        endpointIndex = previousIndex & 0xFFFF;
                        selectorIndex = previousIndex >>> 16;
                        copiedFromPreviousFrame = true;
                    } else if (blockX == 0 || blockY == 0) {
                        throw new BasisDecodeException(
                                "Invalid ETC1S upper-left endpoint predictor at image edge");
                    } else {
                        endpointIndex = predictionState.endpointIndices[currentRow ^ 1][blockX - 1];
                    }
                } else {
                    endpointIndex = reader.decodeHuffman(tables.getEndpointDeltaTable())
                            + previousEndpointIndex;
                    if (endpointIndex >= palettes.getEndpointCount()) {
                        endpointIndex -= palettes.getEndpointCount();
                    }
                }

                predictionState.endpointIndices[currentRow][blockX] = endpointIndex;
                previousEndpointIndex = endpointIndex;

                if (!copiedFromPreviousFrame) {
                    if (currentSelectorRunCount > 0) {
                        currentSelectorRunCount--;
                        selectorIndex = readSelectorFromHistory(selectorHistory, 0);
                    } else {
                        int selectorSymbol = reader.decodeHuffman(tables.getSelectorTable());
                        if (selectorSymbol == selectorRleSymbol) {
                            int runSymbol = reader.decodeHuffman(tables.getSelectorHistoryRunLengthTable());
                            if (runSymbol == SELECTOR_HISTORY_BUF_RLE_COUNT_TOTAL - 1) {
                                currentSelectorRunCount = reader.decodeVariableLengthCode(7)
                                        + SELECTOR_HISTORY_BUF_RLE_COUNT_THRESH;
                            } else {
                                currentSelectorRunCount = runSymbol + SELECTOR_HISTORY_BUF_RLE_COUNT_THRESH;
                            }
                            selectorIndex = readSelectorFromHistory(selectorHistory, 0);
                            currentSelectorRunCount--;
                        } else if (selectorSymbol >= palettes.getSelectorCount()) {
                            selectorIndex = readSelectorFromHistory(
                                    selectorHistory,
                                    selectorSymbol - palettes.getSelectorCount());
                        } else {
                            selectorIndex = selectorSymbol;
                            selectorHistory.add(selectorIndex);
                        }
                    }
                }

                if (endpointIndex < 0 || endpointIndex >= palettes.getEndpointCount()
                        || selectorIndex < 0 || selectorIndex >= palettes.getSelectorCount()) {
                    throw new BasisDecodeException("ETC1S block references an invalid palette index");
                }
                if (previousFrameIndices != null) {
                    if (endpointIndex > 0xFFFF || selectorIndex > 0xFFFF) {
                        throw new BasisDecodeException("ETC1S video palette index exceeds frame-state range");
                    }
                    previousFrameIndices[blockX + blockY * blocksX] = endpointIndex | (selectorIndex << 16);
                }
                if (outputMode == OutputMode.BC1 || outputMode == OutputMode.BC1_NO_THREECOLOR) {
                    writeBc1Block(
                            output,
                            blocksX,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex),
                            outputMode == OutputMode.BC1);
                } else if (outputMode == OutputMode.BC7_COLOR) {
                    writeBc7ColorBlock(
                            output,
                            blocksX,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex));
                } else if (outputMode == OutputMode.BC7_ALPHA) {
                    writeBc7AlphaBlock(
                            output,
                            blocksX,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex));
                } else if (outputMode == OutputMode.ETC1) {
                    writeEtc1Block(
                            output,
                            blocksX,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex));
                } else if (outputMode == OutputMode.ETC2_RGBA) {
                    writeEtc2RgbaBlock(
                            output,
                            blocksX,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex));
                } else if (outputMode == OutputMode.ETC2_EAC_A8) {
                    writeEacA8Block(
                            output,
                            blocksX,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex));
                } else if (outputMode == OutputMode.ETC2_EAC_R11) {
                    writeEacR11Block(
                            output,
                            blocksX,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex));
                } else if (outputMode == OutputMode.DXT5_ALPHA) {
                    writeDxt5AlphaBlock(
                            output,
                            blocksX,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex));
                } else {
                    writeRgbaBlock(
                            output,
                            width,
                            height,
                            blockX,
                            blockY,
                            palettes.getEndpoint(endpointIndex),
                            palettes.getSelector(selectorIndex));
                }
            }
        }
        return output;
    }

    private static byte[] allocateOutput(
            int width,
            int height,
            int blocksX,
            int blocksY,
            OutputMode outputMode) {
        int blockCount = Math.multiplyExact(blocksX, blocksY);
        if (outputMode == OutputMode.ETC1
                || outputMode == OutputMode.BC1
                || outputMode == OutputMode.BC1_NO_THREECOLOR) {
            return new byte[Math.multiplyExact(blockCount, 8)];
        }
        if (outputMode == OutputMode.BC7_COLOR || outputMode == OutputMode.BC7_ALPHA) {
            return new byte[Math.multiplyExact(blockCount, 16)];
        }
        if (outputMode == OutputMode.ETC2_EAC_A8
                || outputMode == OutputMode.ETC2_EAC_R11
                || outputMode == OutputMode.DXT5_ALPHA) {
            return new byte[Math.multiplyExact(blockCount, 8)];
        }
        if (outputMode == OutputMode.ETC2_RGBA) {
            return new byte[Math.multiplyExact(blockCount, 16)];
        }
        return new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
    }

    private static int readSelectorFromHistory(ApproxMoveToFront selectorHistory, int historyIndex) {
        if (historyIndex < 0 || historyIndex >= selectorHistory.size()) {
            throw new BasisDecodeException("ETC1S selector history index out of range");
        }
        int selectorIndex = selectorHistory.get(historyIndex);
        if (historyIndex != 0) {
            selectorHistory.use(historyIndex);
        }
        return selectorIndex;
    }

    private static void writeRgbaBlock(
            byte[] rgba,
            int width,
            int height,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        int[][] colors = blockColors(endpoint);
        int maxX = Math.min(4, width - blockX * 4);
        int maxY = Math.min(4, height - blockY * 4);
        for (int y = 0; y < maxY; y++) {
            for (int x = 0; x < maxX; x++) {
                int[] color = colors[selector.getSelector(x, y)];
                int pixelOffset = ((blockY * 4 + y) * width + blockX * 4 + x) * 4;
                rgba[pixelOffset] = (byte) color[0];
                rgba[pixelOffset + 1] = (byte) color[1];
                rgba[pixelOffset + 2] = (byte) color[2];
                rgba[pixelOffset + 3] = (byte) 255;
            }
        }
    }

    private static int[][] blockColors(Etc1sEndpoint endpoint) {
        int red = expand5(endpoint.getRed5());
        int green = expand5(endpoint.getGreen5());
        int blue = expand5(endpoint.getBlue5());
        int[] intensityTable = ETC1_INTENSITY_TABLES[endpoint.getIntensity5()];
        int[][] colors = new int[4][3];
        for (int i = 0; i < colors.length; i++) {
            colors[i][0] = clamp255(red + intensityTable[i]);
            colors[i][1] = clamp255(green + intensityTable[i]);
            colors[i][2] = clamp255(blue + intensityTable[i]);
        }
        return colors;
    }

    private static void writeEtc1Block(
            byte[] blocks,
            int blocksX,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        int blockOffset = (blockY * blocksX + blockX) * 8;
        writeEtc1Block(blocks, blockOffset, endpoint, selector);
    }

    private static void writeEtc2RgbaBlock(
            byte[] blocks,
            int blocksX,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        int blockOffset = (blockY * blocksX + blockX) * 16;
        System.arraycopy(OPAQUE_ETC2_EAC_A8_BLOCK, 0, blocks, blockOffset, OPAQUE_ETC2_EAC_A8_BLOCK.length);
        writeEtc1Block(blocks, blockOffset + OPAQUE_ETC2_EAC_A8_BLOCK.length, endpoint, selector);
    }

    private static void writeEacA8Block(
            byte[] blocks,
            int blocksX,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        int blockOffset = (blockY * blocksX + blockX) * 8;
        int lowSelector = selector.getLowSelector();
        int highSelector = selector.getHighSelector();
        if (lowSelector == highSelector) {
            blocks[blockOffset] = (byte) blockColor(endpoint, lowSelector);
            blocks[blockOffset + 1] = 0x1D;
            blocks[blockOffset + 2] = (byte) 0x92;
            blocks[blockOffset + 3] = 0x49;
            blocks[blockOffset + 4] = 0x24;
            blocks[blockOffset + 5] = (byte) 0x92;
            blocks[blockOffset + 6] = 0x49;
            blocks[blockOffset + 7] = 0x24;
            return;
        }

        EacConversion conversion = eacA8Conversion(endpoint, lowSelector, highSelector);
        blocks[blockOffset] = (byte) conversion.base;
        blocks[blockOffset + 1] = (byte) ((conversion.multiplier << 4) | conversion.table);

        long selectorBits = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int sourceSelector = selector.getSelector(x, y);
                int eacSelector = conversion.selector[sourceSelector];
                int bitOffset = 45 - (y + x * 4) * 3;
                selectorBits |= (long) eacSelector << bitOffset;
            }
        }
        for (int i = 0; i < 6; i++) {
            blocks[blockOffset + 2 + i] = (byte) (selectorBits >>> (40 - i * 8));
        }
    }

    private static void writeEacR11Block(
            byte[] blocks,
            int blocksX,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        int blockOffset = (blockY * blocksX + blockX) * 8;
        int lowSelector = selector.getLowSelector();
        int highSelector = selector.getHighSelector();
        if (lowSelector == highSelector) {
            blocks[blockOffset] = (byte) blockColor(endpoint, lowSelector);
            blocks[blockOffset + 1] = 0x1D;
            blocks[blockOffset + 2] = (byte) 0x92;
            blocks[blockOffset + 3] = 0x49;
            blocks[blockOffset + 4] = 0x24;
            blocks[blockOffset + 5] = (byte) 0x92;
            blocks[blockOffset + 6] = 0x49;
            blocks[blockOffset + 7] = 0x24;
            return;
        }

        EacConversion conversion = eacR11Conversion(endpoint, lowSelector, highSelector);
        blocks[blockOffset] = (byte) conversion.base;
        blocks[blockOffset + 1] = (byte) ((conversion.multiplier << 4) | conversion.table);

        long selectorBits = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int sourceSelector = selector.getSelector(x, y);
                int eacSelector = conversion.selector[sourceSelector];
                int bitOffset = 45 - (y + x * 4) * 3;
                selectorBits |= (long) eacSelector << bitOffset;
            }
        }
        for (int i = 0; i < 6; i++) {
            blocks[blockOffset + 2 + i] = (byte) (selectorBits >>> (40 - i * 8));
        }
    }

    private static void writeDxt5AlphaBlock(
            byte[] blocks,
            int blocksX,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        int blockOffset = (blockY * blocksX + blockX) * 8;
        int lowSelector = selector.getLowSelector();
        int highSelector = selector.getHighSelector();
        if (lowSelector == highSelector) {
            blocks[blockOffset] = (byte) blockColor(endpoint, lowSelector);
            blocks[blockOffset + 1] = blocks[blockOffset];
            return;
        }

        int[] selectorMap;
        if (selector.getUniqueSelectorCount() == 2) {
            int[][] colors = blockColors(endpoint);
            blocks[blockOffset] = (byte) colors[lowSelector][0];
            blocks[blockOffset + 1] = (byte) colors[highSelector][0];
            selectorMap = new int[4];
            selectorMap[lowSelector] = 0;
            selectorMap[highSelector] = 1;
        } else {
            Dxt5AlphaConversion conversion = dxt5AlphaConversion(endpoint, lowSelector, highSelector);
            blocks[blockOffset] = (byte) conversion.low;
            blocks[blockOffset + 1] = (byte) conversion.high;
            selectorMap = conversion.selector;
        }

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                setDxt5AlphaSelector(
                        blocks,
                        blockOffset,
                        x,
                        y,
                        selectorMap[selector.getSelector(x, y)]);
            }
        }
    }

    private static void writeBc1Block(
            byte[] blocks,
            int blocksX,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector,
            boolean useThreeColorBlocks) {
        int blockOffset = (blockY * blocksX + blockX) * 8;
        Bc1Tables.writeBlock(blocks, blockOffset, endpoint, selector, useThreeColorBlocks);
    }

    private static void writeBc7ColorBlock(
            byte[] blocks,
            int blocksX,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        Bc7Mode5.writeColorBlock(blocks, (blockY * blocksX + blockX) * 16, endpoint, selector);
    }

    private static void writeBc7AlphaBlock(
            byte[] blocks,
            int blocksX,
            int blockX,
            int blockY,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        Bc7Mode5.writeAlphaBlock(blocks, (blockY * blocksX + blockX) * 16, endpoint, selector);
    }

    private static void writeEtc1Block(
            byte[] blocks,
            int blockOffset,
            Etc1sEndpoint endpoint,
            Etc1sSelector selector) {
        setBlockBits(blocks, blockOffset, 59, 5, endpoint.getRed5());
        setBlockBits(blocks, blockOffset, 51, 5, endpoint.getGreen5());
        setBlockBits(blocks, blockOffset, 43, 5, endpoint.getBlue5());

        int table = endpoint.getIntensity5();
        blocks[blockOffset + 3] = (byte) (2 | (table << 5) | (table << 2));

        byte[] selectors = selector.getEtc1Bytes();
        System.arraycopy(selectors, 0, blocks, blockOffset + 4, selectors.length);
    }

    private static void setBlockBits(byte[] block, int blockOffset, int bitOffset, int bitCount, int value) {
        int byteOffset = blockOffset + 7 - (bitOffset >>> 3);
        int byteBitOffset = bitOffset & 7;
        int mask = (1 << bitCount) - 1;
        int current = Byte.toUnsignedInt(block[byteOffset]);
        current &= ~(mask << byteBitOffset);
        current |= (value & mask) << byteBitOffset;
        block[byteOffset] = (byte) current;
    }

    private static void setLittleEndianBits(
            byte[] block,
            int blockOffset,
            int bitOffset,
            int bitCount,
            int value) {
        int remaining = bitCount;
        int currentOffset = bitOffset;
        int currentValue = value;
        while (remaining > 0) {
            int byteIndex = blockOffset + (currentOffset >>> 3);
            int byteBitOffset = currentOffset & 7;
            int bits = Math.min(8 - byteBitOffset, remaining);
            int mask = (1 << bits) - 1;
            int byteValue = Byte.toUnsignedInt(block[byteIndex]);
            byteValue &= ~(mask << byteBitOffset);
            byteValue |= (currentValue & mask) << byteBitOffset;
            block[byteIndex] = (byte) byteValue;
            currentValue >>>= bits;
            currentOffset += bits;
            remaining -= bits;
        }
    }

    private static void applyBc7AlphaBlocks(
            byte[] bc7Blocks,
            byte[] alphaData,
            int alphaOffset,
            int alphaLength,
            int width,
            int height,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            VideoState videoState,
            int levelIndex) {
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        int totalBlocks = Math.multiplyExact(blocksX, blocksY);
        int[] previousFrameIndices = videoState == null
                ? null
                : videoState.previousFrameIndices(true, levelIndex, totalBlocks);
        BasisBitReader reader = new BasisBitReader(alphaData, alphaOffset, alphaLength);
        ApproxMoveToFront selectorHistory = new ApproxMoveToFront(tables.getSelectorHistoryBufferSize());
        BlockPredictionState predictionState = new BlockPredictionState(blocksX);

        int currentSelectorRunCount = 0;
        int currentPredictionBits = 0;
        int previousEndpointPredictionSymbol = 0;
        int endpointPredictionRepeatCount = 0;
        int previousEndpointIndex = 0;
        int selectorRleSymbol = palettes.getSelectorCount() + selectorHistory.size();

        for (int blockY = 0; blockY < blocksY; blockY++) {
            int currentRow = blockY & 1;
            for (int blockX = 0; blockX < blocksX; blockX++) {
                if ((blockX & 1) == 0) {
                    if ((blockY & 1) == 0) {
                        if (endpointPredictionRepeatCount != 0) {
                            endpointPredictionRepeatCount--;
                            currentPredictionBits = previousEndpointPredictionSymbol;
                        } else {
                            currentPredictionBits = reader.decodeHuffman(tables.getEndpointPredictionTable());
                            if (currentPredictionBits == ENDPOINT_PRED_REPEAT_LAST_SYMBOL) {
                                endpointPredictionRepeatCount = reader.decodeVariableLengthCode(
                                        ENDPOINT_PRED_COUNT_VLC_BITS) + ENDPOINT_PRED_MIN_REPEAT_COUNT - 1;
                                currentPredictionBits = previousEndpointPredictionSymbol;
                            } else {
                                previousEndpointPredictionSymbol = currentPredictionBits;
                            }
                        }
                        predictionState.predictionBits[currentRow ^ 1][blockX] = currentPredictionBits >>> 4;
                    } else {
                        currentPredictionBits = predictionState.predictionBits[currentRow][blockX];
                    }
                }

                int prediction = currentPredictionBits & 3;
                currentPredictionBits >>>= 2;
                int endpointIndex;
                int selectorIndex = 0;
                boolean copiedFromPreviousFrame = false;
                if (prediction == 0) {
                    if (blockX == 0) {
                        throw new BasisDecodeException("Invalid ETC1S left endpoint predictor at image edge");
                    }
                    endpointIndex = previousEndpointIndex;
                } else if (prediction == 1) {
                    if (blockY == 0) {
                        throw new BasisDecodeException(
                                "Invalid ETC1S upper endpoint predictor at image edge");
                    }
                    endpointIndex = predictionState.endpointIndices[currentRow ^ 1][blockX];
                } else if (prediction == 2) {
                    if (previousFrameIndices != null) {
                        int previousIndex = previousFrameIndices[blockX + blockY * blocksX];
                        endpointIndex = previousIndex & 0xFFFF;
                        selectorIndex = previousIndex >>> 16;
                        copiedFromPreviousFrame = true;
                    } else if (blockX == 0 || blockY == 0) {
                        throw new BasisDecodeException(
                                "Invalid ETC1S upper-left endpoint predictor at image edge");
                    } else {
                        endpointIndex = predictionState.endpointIndices[currentRow ^ 1][blockX - 1];
                    }
                } else {
                    endpointIndex = reader.decodeHuffman(tables.getEndpointDeltaTable())
                            + previousEndpointIndex;
                    if (endpointIndex >= palettes.getEndpointCount()) {
                        endpointIndex -= palettes.getEndpointCount();
                    }
                }

                predictionState.endpointIndices[currentRow][blockX] = endpointIndex;
                previousEndpointIndex = endpointIndex;

                if (!copiedFromPreviousFrame) {
                    if (currentSelectorRunCount > 0) {
                        currentSelectorRunCount--;
                        selectorIndex = readSelectorFromHistory(selectorHistory, 0);
                    } else {
                        int selectorSymbol = reader.decodeHuffman(tables.getSelectorTable());
                        if (selectorSymbol == selectorRleSymbol) {
                            int runSymbol = reader.decodeHuffman(tables.getSelectorHistoryRunLengthTable());
                            if (runSymbol == SELECTOR_HISTORY_BUF_RLE_COUNT_TOTAL - 1) {
                                currentSelectorRunCount = reader.decodeVariableLengthCode(7)
                                        + SELECTOR_HISTORY_BUF_RLE_COUNT_THRESH;
                            } else {
                                currentSelectorRunCount = runSymbol + SELECTOR_HISTORY_BUF_RLE_COUNT_THRESH;
                            }
                            selectorIndex = readSelectorFromHistory(selectorHistory, 0);
                            currentSelectorRunCount--;
                        } else if (selectorSymbol >= palettes.getSelectorCount()) {
                            selectorIndex = readSelectorFromHistory(
                                    selectorHistory,
                                    selectorSymbol - palettes.getSelectorCount());
                        } else {
                            selectorIndex = selectorSymbol;
                            selectorHistory.add(selectorIndex);
                        }
                    }
                }

                if (endpointIndex < 0 || endpointIndex >= palettes.getEndpointCount()
                        || selectorIndex < 0 || selectorIndex >= palettes.getSelectorCount()) {
                    throw new BasisDecodeException("ETC1S block references an invalid palette index");
                }
                if (previousFrameIndices != null) {
                    if (endpointIndex > 0xFFFF || selectorIndex > 0xFFFF) {
                        throw new BasisDecodeException("ETC1S video palette index exceeds frame-state range");
                    }
                    previousFrameIndices[blockX + blockY * blocksX] = endpointIndex | (selectorIndex << 16);
                }
                writeBc7AlphaBlock(
                        bc7Blocks,
                        blocksX,
                        blockX,
                        blockY,
                        palettes.getEndpoint(endpointIndex),
                        palettes.getSelector(selectorIndex));
            }
        }
    }

    private static EacConversion eacA8Conversion(Etc1sEndpoint endpoint, int lowSelector, int highSelector) {
        int rangeIndex = eacSelectorRangeIndex(lowSelector, highSelector);
        int cacheIndex = endpoint.getRed5() + endpoint.getIntensity5() * 32;
        EacConversion cached = EAC_A8_CONVERSION_CACHE[cacheIndex][rangeIndex];
        if (cached != null) {
            return cached;
        }
        EacConversion computed = computeEacA8Conversion(
                endpoint.getRed5(),
                endpoint.getIntensity5(),
                rangeIndex);
        EAC_A8_CONVERSION_CACHE[cacheIndex][rangeIndex] = computed;
        return computed;
    }

    private static EacConversion eacR11Conversion(Etc1sEndpoint endpoint, int lowSelector, int highSelector) {
        int rangeIndex = eacSelectorRangeIndex(lowSelector, highSelector);
        int cacheIndex = endpoint.getRed5() + endpoint.getIntensity5() * 32;
        EacConversion cached = EAC_R11_CONVERSION_CACHE[cacheIndex][rangeIndex];
        if (cached != null) {
            return cached;
        }
        EacConversion computed = computeEacR11Conversion(
                endpoint.getRed5(),
                endpoint.getIntensity5(),
                rangeIndex);
        EAC_R11_CONVERSION_CACHE[cacheIndex][rangeIndex] = computed;
        return computed;
    }

    private static Dxt5AlphaConversion dxt5AlphaConversion(
            Etc1sEndpoint endpoint,
            int lowSelector,
            int highSelector) {
        int rangeIndex = dxt5AlphaSelectorRangeIndex(lowSelector, highSelector);
        int cacheIndex = endpoint.getRed5() + endpoint.getIntensity5() * 32;
        Dxt5AlphaConversion cached = DXT5_ALPHA_CONVERSION_CACHE[cacheIndex][rangeIndex];
        if (cached != null) {
            return cached;
        }
        Dxt5AlphaConversion computed = computeDxt5AlphaConversion(
                endpoint.getRed5(),
                endpoint.getIntensity5(),
                rangeIndex);
        DXT5_ALPHA_CONVERSION_CACHE[cacheIndex][rangeIndex] = computed;
        return computed;
    }

    private static int eacSelectorRangeIndex(int lowSelector, int highSelector) {
        for (int i = 0; i < EAC_SELECTOR_RANGES.length; i++) {
            if (EAC_SELECTOR_RANGES[i][0] == lowSelector && EAC_SELECTOR_RANGES[i][1] == highSelector) {
                return i;
            }
        }
        return 0;
    }

    private static int dxt5AlphaSelectorRangeIndex(int lowSelector, int highSelector) {
        for (int i = 0; i < DXT5_ALPHA_SELECTOR_RANGES.length; i++) {
            if (DXT5_ALPHA_SELECTOR_RANGES[i][0] == lowSelector
                    && DXT5_ALPHA_SELECTOR_RANGES[i][1] == highSelector) {
                return i;
            }
        }
        return 0;
    }

    private static EacConversion computeEacA8Conversion(int base5, int intensity, int rangeIndex) {
        int lowSelector = EAC_SELECTOR_RANGES[rangeIndex][0];
        int highSelector = EAC_SELECTOR_RANGES[rangeIndex][1];
        int[] pixels = new int[highSelector - lowSelector + 1];
        for (int selector = lowSelector; selector <= highSelector; selector++) {
            pixels[selector - lowSelector] = blockColor(base5, intensity, selector);
        }

        long bestError = Long.MAX_VALUE;
        int bestBase = 0;
        int bestTable = 0;
        int bestMultiplier = 1;
        int[] bestSelectors = new int[pixels.length];
        for (int base = 0; base < 256; base++) {
            for (int multiplier = 1; multiplier < 16; multiplier++) {
                for (int table = 0; table < EAC_MODIFIER_TABLE.length; table++) {
                    long totalError = 0;
                    int[] currentSelectors = new int[pixels.length];
                    for (int i = 0; i < pixels.length; i++) {
                        int pixel = pixels[i];
                        int bestSelectorError = Integer.MAX_VALUE;
                        int bestSelector = 0;
                        for (int selector = 0; selector < 8; selector++) {
                            int value = clamp255(base + multiplier * EAC_MODIFIER_TABLE[table][selector]);
                            int error = Math.abs(pixel - value);
                            if (error < bestSelectorError) {
                                bestSelectorError = error;
                                bestSelector = selector;
                            }
                        }
                        currentSelectors[i] = bestSelector;
                        totalError += bestSelectorError * bestSelectorError;
                        if (totalError >= bestError) {
                            break;
                        }
                    }
                    if (totalError < bestError) {
                        bestError = totalError;
                        bestBase = base;
                        bestTable = table;
                        bestMultiplier = multiplier;
                        bestSelectors = currentSelectors;
                    }
                }
            }
        }

        int[] selectorMap = new int[4];
        for (int selector = lowSelector; selector <= highSelector; selector++) {
            selectorMap[selector] = bestSelectors[selector - lowSelector];
        }
        return new EacConversion(bestBase, bestTable, bestMultiplier, selectorMap);
    }

    private static EacConversion computeEacR11Conversion(int base5, int intensity, int rangeIndex) {
        int lowSelector = EAC_SELECTOR_RANGES[rangeIndex][0];
        int highSelector = EAC_SELECTOR_RANGES[rangeIndex][1];
        int[] pixels = new int[highSelector - lowSelector + 1];
        for (int selector = lowSelector; selector <= highSelector; selector++) {
            pixels[selector - lowSelector] = blockColor(base5, intensity, selector);
        }

        long bestError = Long.MAX_VALUE;
        int bestBase = 0;
        int bestTable = 0;
        int bestMultiplier = 0;
        int[] bestSelectors = new int[pixels.length];
        for (int base = 0; base < 256; base++) {
            for (int multiplier = 0; multiplier < 16; multiplier++) {
                int scaledMultiplier = multiplier == 0 ? 1 : multiplier * 8;
                for (int table = 0; table < EAC_MODIFIER_TABLE.length; table++) {
                    long totalError = 0;
                    int[] currentSelectors = new int[pixels.length];
                    for (int i = 0; i < pixels.length; i++) {
                        int pixel = (pixels[i] * 2047 + 128) / 255;
                        int bestSelectorError = Integer.MAX_VALUE;
                        int bestSelector = 0;
                        for (int selector = 0; selector < 8; selector++) {
                            int value = scaledMultiplier * EAC_MODIFIER_TABLE[table][selector] + base * 8 + 4;
                            value = clamp2047(value);
                            int error = Math.abs(pixel - value);
                            if (error < bestSelectorError) {
                                bestSelectorError = error;
                                bestSelector = selector;
                            }
                        }
                        currentSelectors[i] = bestSelector;
                        totalError += (long) bestSelectorError * bestSelectorError;
                        if (totalError >= bestError) {
                            break;
                        }
                    }
                    if (totalError < bestError) {
                        bestError = totalError;
                        bestBase = base;
                        bestTable = table;
                        bestMultiplier = multiplier;
                        bestSelectors = currentSelectors;
                    }
                }
            }
        }

        int[] selectorMap = new int[4];
        for (int selector = lowSelector; selector <= highSelector; selector++) {
            selectorMap[selector] = bestSelectors[selector - lowSelector];
        }
        return new EacConversion(bestBase, bestTable, bestMultiplier, selectorMap);
    }

    private static Dxt5AlphaConversion computeDxt5AlphaConversion(int base5, int intensity, int rangeIndex) {
        int tableIndex = (base5 + intensity * 32) * DXT5_ALPHA_SELECTOR_RANGES.length + rangeIndex;
        int entry = DXT5_ALPHA_CONVERSION_TABLE[tableIndex];
        int selectorBits = entry >>> 16;
        int[] selectorMap = new int[4];
        for (int selector = 0; selector < selectorMap.length; selector++) {
            selectorMap[selector] = (selectorBits >>> (selector * 3)) & 7;
        }
        return new Dxt5AlphaConversion(entry & 0xFF, (entry >>> 8) & 0xFF, selectorMap);
    }

    private static void setDxt5AlphaSelector(
            byte[] blocks,
            int blockOffset,
            int x,
            int y,
            int selector) {
        int bitIndex = (y * 4 + x) * 3;
        int byteIndex = blockOffset + 2 + (bitIndex >>> 3);
        int bitOffset = bitIndex & 7;
        int value = Byte.toUnsignedInt(blocks[byteIndex]);
        if (byteIndex < blockOffset + 7) {
            value |= Byte.toUnsignedInt(blocks[byteIndex + 1]) << 8;
        }
        value &= ~(7 << bitOffset);
        value |= (selector & 7) << bitOffset;
        blocks[byteIndex] = (byte) value;
        if (byteIndex < blockOffset + 7) {
            blocks[byteIndex + 1] = (byte) (value >>> 8);
        }
    }

    private static int blockColor(Etc1sEndpoint endpoint, int selector) {
        return blockColor(endpoint.getRed5(), endpoint.getIntensity5(), selector);
    }

    private static int blockColor(int base5, int intensity, int selector) {
        return clamp255(expand5(base5) + ETC1_INTENSITY_TABLES[intensity][selector]);
    }

    private static int expand5(int value) {
        return (value << 3) | (value >>> 2);
    }

    private static int clamp255(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }

    private static int clamp2047(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 2047) {
            return 2047;
        }
        return value;
    }

    private static final class BlockPredictionState {
        private final int[][] predictionBits;
        private final int[][] endpointIndices;

        private BlockPredictionState(int blocksX) {
            predictionBits = new int[2][blocksX];
            endpointIndices = new int[2][blocksX];
        }
    }

    private enum OutputMode {
        RGBA8,
        ETC1,
        ETC2_RGBA,
        ETC2_EAC_A8,
        ETC2_EAC_R11,
        DXT5_ALPHA,
        BC1,
        BC1_NO_THREECOLOR,
        BC7_COLOR,
        BC7_ALPHA
    }

    static final class VideoState {
        private final int[][][] previousFrameIndices = new int[2][][];

        int[] previousFrameIndices(boolean alphaSlice, int levelIndex, int totalBlocks) {
            if (levelIndex < 0) {
                throw new BasisDecodeException("ETC1S video level index is malformed: " + levelIndex);
            }
            int alphaIndex = alphaSlice ? 1 : 0;
            int[][] levels = previousFrameIndices[alphaIndex];
            if (levels == null || levelIndex >= levels.length) {
                int[][] grownLevels = new int[levelIndex + 1][];
                if (levels != null) {
                    System.arraycopy(levels, 0, grownLevels, 0, levels.length);
                }
                previousFrameIndices[alphaIndex] = grownLevels;
                levels = grownLevels;
            }
            int[] indices = levels[levelIndex];
            if (indices == null || indices.length < totalBlocks) {
                indices = new int[totalBlocks];
                levels[levelIndex] = indices;
            }
            return indices;
        }
    }

    private static final class EacConversion {
        private final int base;
        private final int table;
        private final int multiplier;
        private final int[] selector;

        private EacConversion(int base, int table, int multiplier, int[] selector) {
            this.base = base;
            this.table = table;
            this.multiplier = multiplier;
            this.selector = selector;
        }
    }

    private static final class Dxt5AlphaConversion {
        private final int low;
        private final int high;
        private final int[] selector;

        private Dxt5AlphaConversion(int low, int high, int[] selector) {
            this.low = low;
            this.high = high;
            this.selector = selector;
        }
    }

    private static final class Bc7Mode5 {
        private static final int[][] SELECTOR_RANGES = {
            {0, 3},
            {1, 3},
            {0, 2},
            {1, 2},
            {2, 3},
            {0, 1}
        };
        private static final int[][] SELECTOR_MAPPINGS = {
            {0, 0, 1, 1},
            {0, 0, 1, 2},
            {0, 0, 1, 3},
            {0, 0, 2, 3},
            {0, 1, 1, 1},
            {0, 1, 2, 2},
            {0, 1, 2, 3},
            {0, 2, 3, 3},
            {1, 2, 2, 2},
            {1, 2, 3, 3}
        };
        private static final int[][] SELECTOR_RANGE_INDEX = createSelectorRangeIndex();
        private static final int[] EQUALS_SELECTOR_ONE = createEqualsSelectorOneTable();

        private Bc7Mode5() {
        }

        private static void writeColorBlock(
                byte[] blocks,
                int blockOffset,
                Etc1sEndpoint endpoint,
                Etc1sSelector selector) {
            for (int i = 0; i < 16; i++) {
                blocks[blockOffset + i] = 0;
            }
            setLittleEndianBits(blocks, blockOffset, 0, 6, 1 << 5);
            setAlphaEndpoint(blocks, blockOffset, 255, 255);

            int lowSelector = selector.getLowSelector();
            int highSelector = selector.getHighSelector();
            int intensity = endpoint.getIntensity5();

            if (selector.getUniqueSelectorCount() == 1) {
                int[] color = blockColors(endpoint)[lowSelector];
                int red = EQUALS_SELECTOR_ONE[color[0]];
                int green = EQUALS_SELECTOR_ONE[color[1]];
                int blue = EQUALS_SELECTOR_ONE[color[2]];
                setColorEndpoints(
                        blocks,
                        blockOffset,
                        red & 0xFF,
                        red >>> 8,
                        green & 0xFF,
                        green >>> 8,
                        blue & 0xFF,
                        blue >>> 8);
                setLittleEndianBits(blocks, blockOffset, 66, 31, 0x2AAAAAAB);
                return;
            }

            if (selector.getUniqueSelectorCount() == 2) {
                writeTwoSelectorColorBlock(
                        blocks, blockOffset, endpoint, selector, lowSelector, highSelector);
                return;
            }

            int rangeIndex = selectorRangeIndex(lowSelector, highSelector);
            int bestError = Integer.MAX_VALUE;
            int bestMapping = 0;
            for (int mapping = 0; mapping < SELECTOR_MAPPINGS.length; mapping++) {
                int red = colorTableEntry(intensity, endpoint.getRed5(), rangeIndex, mapping);
                int green = colorTableEntry(intensity, endpoint.getGreen5(), rangeIndex, mapping);
                int blue = colorTableEntry(intensity, endpoint.getBlue5(), rangeIndex, mapping);
                int error = (red >>> 16) + (green >>> 16) + (blue >>> 16);
                if (error < bestError) {
                    bestError = error;
                    bestMapping = mapping;
                }
            }

            int red = colorTableEntry(intensity, endpoint.getRed5(), rangeIndex, bestMapping);
            int green = colorTableEntry(intensity, endpoint.getGreen5(), rangeIndex, bestMapping);
            int blue = colorTableEntry(intensity, endpoint.getBlue5(), rangeIndex, bestMapping);
            int[] selectorMap = SELECTOR_MAPPINGS[bestMapping];
            int selectorInvert = 0;
            if ((selectorMap[selector.getSelector(0, 0)] & 2) != 0) {
                setColorEndpoints(
                        blocks,
                        blockOffset,
                        (red >>> 8) & 0xFF,
                        red & 0xFF,
                        (green >>> 8) & 0xFF,
                        green & 0xFF,
                        (blue >>> 8) & 0xFF,
                        blue & 0xFF);
                selectorInvert = 3;
            } else {
                setColorEndpoints(
                        blocks,
                        blockOffset,
                        red & 0xFF,
                        (red >>> 8) & 0xFF,
                        green & 0xFF,
                        (green >>> 8) & 0xFF,
                        blue & 0xFF,
                        (blue >>> 8) & 0xFF);
            }

            int outputBitOffset = 0;
            int outputBits = 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int outputSelector = selectorMap[selector.getSelector(x, y)] ^ selectorInvert;
                    outputBits |= outputSelector << outputBitOffset;
                    outputBitOffset += (x | y) == 0 ? 1 : 2;
                }
            }
            setLittleEndianBits(blocks, blockOffset, 66, 31, outputBits);
        }

        private static void writeAlphaBlock(
                byte[] blocks,
                int blockOffset,
                Etc1sEndpoint endpoint,
                Etc1sSelector selector) {
            int lowSelector = selector.getLowSelector();
            int highSelector = selector.getHighSelector();
            int intensity = endpoint.getIntensity5();

            if (selector.getUniqueSelectorCount() == 1) {
                int alpha = blockColors(endpoint)[lowSelector][1];
                setAlphaEndpoint(blocks, blockOffset, alpha, alpha);
                return;
            }

            if (selector.getUniqueSelectorCount() == 2) {
                writeTwoSelectorAlphaBlock(
                        blocks, blockOffset, endpoint, selector, lowSelector, highSelector);
                return;
            }

            int entry = alphaTableEntry(
                    intensity,
                    endpoint.getRed5(),
                    selectorRangeIndex(lowSelector, highSelector));
            int low = entry & 0xFF;
            int high = (entry >>> 8) & 0xFF;
            setAlphaEndpoint(blocks, blockOffset, low, high);

            int outputBitOffset = 0;
            int outputBits = 0;
            int selectorTransform = entry >>> 16;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int sourceSelector = selector.getSelector(x, y);
                    int outputSelector = (selectorTransform >>> (sourceSelector * 2)) & 3;
                    int bitCount = 2;
                    if ((x | y) == 0) {
                        if ((outputSelector & 2) != 0) {
                            setAlphaEndpoint(blocks, blockOffset, high, low);
                            selectorTransform ^= 0xFF;
                            outputSelector ^= 3;
                        }
                        bitCount = 1;
                    }
                    outputBits |= outputSelector << outputBitOffset;
                    outputBitOffset += bitCount;
                }
            }
            setLittleEndianBits(blocks, blockOffset, 97, 31, outputBits);
        }

        private static void writeTwoSelectorColorBlock(
                byte[] blocks,
                int blockOffset,
                Etc1sEndpoint endpoint,
                Etc1sSelector selector,
                int lowSelector,
                int highSelector) {
            int[][] colors = blockColors(endpoint);
            int red0 = colors[lowSelector][0];
            int green0 = colors[lowSelector][1];
            int blue0 = colors[lowSelector][2];
            int red1 = colors[highSelector][0];
            int green1 = colors[highSelector][1];
            int blue1 = colors[highSelector][2];
            setColorEndpoints(blocks, blockOffset, red0 >>> 1, red1 >>> 1, green0 >>> 1, green1 >>> 1,
                    blue0 >>> 1, blue1 >>> 1);

            int outputLowSelector = 0;
            int outputBitOffset = 0;
            int outputBits = 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int sourceSelector = selector.getSelector(x, y);
                    int outputSelector = sourceSelector == lowSelector
                            ? outputLowSelector
                            : (3 ^ outputLowSelector);
                    int bitCount = 2;
                    if ((x | y) == 0) {
                        if ((outputSelector & 2) != 0) {
                            setColorEndpoints(
                                    blocks,
                                    blockOffset,
                                    red1 >>> 1,
                                    red0 >>> 1,
                                    green1 >>> 1,
                                    green0 >>> 1,
                                    blue1 >>> 1,
                                    blue0 >>> 1);
                            outputLowSelector = 3;
                            outputSelector = 0;
                        }
                        bitCount = 1;
                    }
                    outputBits |= outputSelector << outputBitOffset;
                    outputBitOffset += bitCount;
                }
            }
            setLittleEndianBits(blocks, blockOffset, 66, 31, outputBits);
        }

        private static void writeTwoSelectorAlphaBlock(
                byte[] blocks,
                int blockOffset,
                Etc1sEndpoint endpoint,
                Etc1sSelector selector,
                int lowSelector,
                int highSelector) {
            int[][] colors = blockColors(endpoint);
            int alpha0 = colors[lowSelector][1];
            int alpha1 = colors[highSelector][1];
            setAlphaEndpoint(blocks, blockOffset, alpha0, alpha1);

            int outputLowSelector = 0;
            int outputBitOffset = 0;
            int outputBits = 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int sourceSelector = selector.getSelector(x, y);
                    int outputSelector = sourceSelector == lowSelector
                            ? outputLowSelector
                            : (3 ^ outputLowSelector);
                    int bitCount = 2;
                    if ((x | y) == 0) {
                        if ((outputSelector & 2) != 0) {
                            setAlphaEndpoint(blocks, blockOffset, alpha1, alpha0);
                            outputLowSelector = 3;
                            outputSelector = 0;
                        }
                        bitCount = 1;
                    }
                    outputBits |= outputSelector << outputBitOffset;
                    outputBitOffset += bitCount;
                }
            }
            setLittleEndianBits(blocks, blockOffset, 97, 31, outputBits);
        }

        private static void setColorEndpoints(
                byte[] blocks,
                int blockOffset,
                int red0,
                int red1,
                int green0,
                int green1,
                int blue0,
                int blue1) {
            setLittleEndianBits(blocks, blockOffset, 8, 7, red0);
            setLittleEndianBits(blocks, blockOffset, 15, 7, red1);
            setLittleEndianBits(blocks, blockOffset, 22, 7, green0);
            setLittleEndianBits(blocks, blockOffset, 29, 7, green1);
            setLittleEndianBits(blocks, blockOffset, 36, 7, blue0);
            setLittleEndianBits(blocks, blockOffset, 43, 7, blue1);
        }

        private static void setAlphaEndpoint(byte[] blocks, int blockOffset, int alpha0, int alpha1) {
            setLittleEndianBits(blocks, blockOffset, 50, 8, alpha0);
            setLittleEndianBits(blocks, blockOffset, 58, 6, alpha1 & 63);
            setLittleEndianBits(blocks, blockOffset, 64, 2, alpha1 >>> 6);
        }

        private static int colorTableEntry(int intensity, int base5, int rangeIndex, int mappingIndex) {
            int index = (intensity * 32 + base5) * (SELECTOR_RANGES.length * SELECTOR_MAPPINGS.length)
                    + rangeIndex * SELECTOR_MAPPINGS.length
                    + mappingIndex;
            return Bc7Mode5Tables.color(index);
        }

        private static int alphaTableEntry(int intensity, int base5, int rangeIndex) {
            int index = intensity * (32 * SELECTOR_RANGES.length)
                    + base5 * SELECTOR_RANGES.length
                    + rangeIndex;
            return Bc7Mode5Tables.alpha(index);
        }

        private static int selectorRangeIndex(int lowSelector, int highSelector) {
            return SELECTOR_RANGE_INDEX[lowSelector][highSelector];
        }

        private static int[][] createSelectorRangeIndex() {
            int[][] table = new int[4][4];
            for (int i = 0; i < SELECTOR_RANGES.length; i++) {
                table[SELECTOR_RANGES[i][0]][SELECTOR_RANGES[i][1]] = i;
            }
            return table;
        }

        private static int[] createEqualsSelectorOneTable() {
            int[] table = new int[256];
            for (int value = 0; value < table.length; value++) {
                int bestError = Integer.MAX_VALUE;
                int bestLow = 0;
                int bestHigh = 0;
                for (int low = 0; low <= 127; low++) {
                    for (int high = 0; high <= 127; high++) {
                        int lowExpanded = (low << 1) | (low >>> 6);
                        int highExpanded = (high << 1) | (high >>> 6);
                        int interpolated = (lowExpanded * (64 - 21) + highExpanded * 21 + 32) >>> 6;
                        int error = Math.abs(interpolated - value);
                        if (error < bestError) {
                            bestError = error;
                            bestLow = low;
                            bestHigh = high;
                        }
                    }
                }
                table[value] = bestLow | (bestHigh << 8);
            }
            return table;
        }
    }

    private static final class Bc1Tables {
        private static final int[][] SELECTOR_RANGES = {
            {0, 3},
            {1, 3},
            {0, 2},
            {1, 2},
            {2, 3},
            {0, 1}
        };
        private static final int[][] SELECTOR_MAPPINGS = {
            {0, 0, 1, 1},
            {0, 0, 1, 2},
            {0, 0, 1, 3},
            {0, 0, 2, 3},
            {0, 1, 1, 1},
            {0, 1, 2, 2},
            {0, 1, 2, 3},
            {0, 2, 3, 3},
            {1, 2, 2, 2},
            {1, 2, 3, 3}
        };
        private static final int[] LINEAR_DXT1_TO_RAW = {0, 2, 3, 1};
        private static final int[] INVERTED_DXT1_RAW = {1, 0, 3, 2};
        private static final int[][] SELECTOR_RANGE_INDEX = createSelectorRangeIndex();
        private static final byte[][] SELECTOR_MAPPING_RAW = createSelectorMappingTable(false);
        private static final byte[][] SELECTOR_MAPPING_RAW_INVERTED = createSelectorMappingTable(true);
        private static final int[] CONVERSION_5 = createConversionTable(31);
        private static final int[] CONVERSION_6 = createConversionTable(63);
        private static final byte[] MATCH5_EQUALS_1 = createBc1MatchTable(31, 31, 1);
        private static final byte[] MATCH5_EQUALS_0 = createBc1MatchTable(0, 31, 0);
        private static final byte[] MATCH6_EQUALS_1 = createBc1MatchTable(63, 63, 1);
        private static final byte[] MATCH6_EQUALS_0 = createBc1MatchTable(0, 63, 0);

        private Bc1Tables() {
        }

        private static void writeBlock(
                byte[] blocks,
                int blockOffset,
                Etc1sEndpoint endpoint,
                Etc1sSelector selector,
                boolean useThreeColorBlocks) {
            int lowSelector = selector.getLowSelector();
            int highSelector = selector.getHighSelector();
            int intensityTable = endpoint.getIntensity5();

            if (lowSelector == highSelector) {
                int[] color = blockColor(endpoint, lowSelector);
                int max16 = (matchHi(MATCH5_EQUALS_1, color[0]) << 11)
                        | (matchHi(MATCH6_EQUALS_1, color[1]) << 5)
                        | matchHi(MATCH5_EQUALS_1, color[2]);
                int min16 = (matchLo(MATCH5_EQUALS_1, color[0]) << 11)
                        | (matchLo(MATCH6_EQUALS_1, color[1]) << 5)
                        | matchLo(MATCH5_EQUALS_1, color[2]);
                int mask = 0xAA;

                if (!useThreeColorBlocks && min16 == max16) {
                    mask = 0;
                    if (min16 > 0) {
                        min16--;
                    } else {
                        max16 = 1;
                        min16 = 0;
                        mask = 0x55;
                    }
                }

                if (max16 < min16) {
                    int temp = max16;
                    max16 = min16;
                    min16 = temp;
                    mask ^= 0x55;
                }

                writeBc1Endpoints(blocks, blockOffset, max16, min16);
                fillSelectors(blocks, blockOffset, mask);
                return;
            }

            if (intensityTable >= 7
                    && selector.getUniqueSelectorCount() == 2
                    && lowSelector == 0
                    && highSelector == 3) {
                int[][] colors = blockColors(endpoint);
                int max16 = (matchHi(MATCH5_EQUALS_0, colors[0][0]) << 11)
                        | (matchHi(MATCH6_EQUALS_0, colors[0][1]) << 5)
                        | matchHi(MATCH5_EQUALS_0, colors[0][2]);
                int min16 = (matchHi(MATCH5_EQUALS_0, colors[3][0]) << 11)
                        | (matchHi(MATCH6_EQUALS_0, colors[3][1]) << 5)
                        | matchHi(MATCH5_EQUALS_0, colors[3][2]);
                int lowBcSelector = 0;
                int highBcSelector = 1;

                if (min16 == max16) {
                    if (min16 > 0) {
                        min16--;
                        lowBcSelector = 0;
                        highBcSelector = 0;
                    } else {
                        max16 = 1;
                        min16 = 0;
                        lowBcSelector = 1;
                        highBcSelector = 1;
                    }
                }

                if (max16 < min16) {
                    int temp = max16;
                    max16 = min16;
                    min16 = temp;
                    lowBcSelector = 1;
                    highBcSelector = 0;
                }

                writeBc1Endpoints(blocks, blockOffset, max16, min16);
                for (int y = 0; y < 4; y++) {
                    int selectorByte = 0;
                    for (int x = 0; x < 4; x++) {
                        int sourceSelector = selector.getSelector(x, y);
                        int bcSelector = sourceSelector == 3 ? highBcSelector : lowBcSelector;
                        selectorByte |= bcSelector << (x * 2);
                    }
                    blocks[blockOffset + 4 + y] = (byte) selectorByte;
                }
                return;
            }

            int rangeIndex = SELECTOR_RANGE_INDEX[lowSelector][highSelector];
            int tableBase = (intensityTable * 32 + endpoint.getRed5())
                    * SELECTOR_RANGES.length * SELECTOR_MAPPINGS.length
                    + rangeIndex * SELECTOR_MAPPINGS.length;
            int tableBaseGreen = (intensityTable * 32 + endpoint.getGreen5())
                    * SELECTOR_RANGES.length * SELECTOR_MAPPINGS.length
                    + rangeIndex * SELECTOR_MAPPINGS.length;
            int tableBaseBlue = (intensityTable * 32 + endpoint.getBlue5())
                    * SELECTOR_RANGES.length * SELECTOR_MAPPINGS.length
                    + rangeIndex * SELECTOR_MAPPINGS.length;

            int bestError = Integer.MAX_VALUE;
            int bestMapping = 0;
            for (int mapping = 0; mapping < SELECTOR_MAPPINGS.length; mapping++) {
                int red = CONVERSION_5[tableBase + mapping];
                int green = CONVERSION_6[tableBaseGreen + mapping];
                int blue = CONVERSION_5[tableBaseBlue + mapping];
                int error = solutionError(red) + solutionError(green) + solutionError(blue);
                if (error < bestError) {
                    bestError = error;
                    bestMapping = mapping;
                }
            }

            int red = CONVERSION_5[tableBase + bestMapping];
            int green = CONVERSION_6[tableBaseGreen + bestMapping];
            int blue = CONVERSION_5[tableBaseBlue + bestMapping];
            int color0 = packUnscaledColor(solutionLo(red), solutionLo(green), solutionLo(blue));
            int color1 = packUnscaledColor(solutionHi(red), solutionHi(green), solutionHi(blue));
            byte[] selectorMap = SELECTOR_MAPPING_RAW[bestMapping];

            if (color0 < color1) {
                int temp = color0;
                color0 = color1;
                color1 = temp;
                selectorMap = SELECTOR_MAPPING_RAW_INVERTED[bestMapping];
            }

            writeBc1Endpoints(blocks, blockOffset, color0, color1);
            if (color0 == color1) {
                fillSelectors(blocks, blockOffset, 0);
                if (!useThreeColorBlocks) {
                    if (color1 > 0) {
                        color1--;
                    } else {
                        color0 = 1;
                        color1 = 0;
                        fillSelectors(blocks, blockOffset, 0x55);
                    }
                    writeBc1Endpoints(blocks, blockOffset, color0, color1);
                }
                return;
            }

            byte[] selectorBytes = selector.getSelectorBytes();
            for (int y = 0; y < 4; y++) {
                blocks[blockOffset + 4 + y] = selectorMap[Byte.toUnsignedInt(selectorBytes[y])];
            }
        }

        private static int[][] createSelectorRangeIndex() {
            int[][] result = new int[4][4];
            for (int i = 0; i < SELECTOR_RANGES.length; i++) {
                result[SELECTOR_RANGES[i][0]][SELECTOR_RANGES[i][1]] = i;
            }
            return result;
        }

        private static byte[][] createSelectorMappingTable(boolean inverted) {
            byte[][] result = new byte[SELECTOR_MAPPINGS.length][256];
            for (int mapping = 0; mapping < SELECTOR_MAPPINGS.length; mapping++) {
                int[] selectorMap = new int[4];
                for (int i = 0; i < selectorMap.length; i++) {
                    int raw = LINEAR_DXT1_TO_RAW[SELECTOR_MAPPINGS[mapping][i]];
                    selectorMap[i] = inverted ? INVERTED_DXT1_RAW[raw] : raw;
                }
                for (int input = 0; input < 256; input++) {
                    int output = 0;
                    for (int selector = 0; selector < 4; selector++) {
                        output |= selectorMap[(input >>> (selector * 2)) & 3] << (selector * 2);
                    }
                    result[mapping][input] = (byte) output;
                }
            }
            return result;
        }

        private static int[] createConversionTable(int maxColor) {
            int[] result = new int[8 * 32 * SELECTOR_RANGES.length * SELECTOR_MAPPINGS.length];
            int entryIndex = 0;
            for (int intensity = 0; intensity < 8; intensity++) {
                for (int base = 0; base < 32; base++) {
                    int[] blockColors = scalarBlockColors(base, intensity);
                    for (int range = 0; range < SELECTOR_RANGES.length; range++) {
                        int lowSelector = SELECTOR_RANGES[range][0];
                        int highSelector = SELECTOR_RANGES[range][1];
                        for (int mapping = 0; mapping < SELECTOR_MAPPINGS.length; mapping++) {
                            int bestLo = 0;
                            int bestHi = 0;
                            int bestError = Integer.MAX_VALUE;
                            for (int hi = 0; hi <= maxColor; hi++) {
                                for (int lo = 0; lo <= maxColor; lo++) {
                                    int[] colors = bc1ScalarColors(lo, hi, maxColor);
                                    int totalError = 0;
                                    for (int selector = lowSelector; selector <= highSelector; selector++) {
                                        int error = blockColors[selector]
                                                - colors[SELECTOR_MAPPINGS[mapping][selector]];
                                        totalError += error * error;
                                    }
                                    if (totalError < bestError) {
                                        bestError = totalError;
                                        bestLo = lo;
                                        bestHi = hi;
                                    }
                                }
                            }
                            result[entryIndex++] = packSolution(bestLo, bestHi, bestError);
                        }
                    }
                }
            }
            return result;
        }

        private static byte[] createBc1MatchTable(int maxLo, int maxHi, int selector) {
            byte[] result = new byte[256 * 2];
            for (int color = 0; color < 256; color++) {
                int lowestError = 256;
                for (int lo = 0; lo <= maxLo; lo++) {
                    for (int hi = 0; hi <= maxHi; hi++) {
                        int loExpanded = maxHi == 63 ? expand6(lo) : expand5(lo);
                        int hiExpanded = maxHi == 63 ? expand6(hi) : expand5(hi);
                        int error;
                        if (selector == 1) {
                            error = Math.abs(((hiExpanded * 2 + loExpanded) / 3) - color);
                            error += (Math.abs(hiExpanded - loExpanded) * 3) / 100;
                        } else {
                            error = Math.abs(hiExpanded - color);
                        }
                        if (error < lowestError) {
                            result[color * 2] = (byte) hi;
                            result[color * 2 + 1] = (byte) lo;
                            lowestError = error;
                        }
                    }
                }
            }
            return result;
        }

        private static int[] scalarBlockColors(int base5, int intensityTable) {
            int base = expand5(base5);
            int[] modifiers = ETC1_INTENSITY_TABLES[intensityTable];
            int[] colors = new int[4];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = clamp255(base + modifiers[i]);
            }
            return colors;
        }

        private static int[] bc1ScalarColors(int lo, int hi, int maxColor) {
            int[] colors = new int[4];
            colors[0] = maxColor == 63 ? expand6(lo) : expand5(lo);
            colors[3] = maxColor == 63 ? expand6(hi) : expand5(hi);
            colors[1] = (colors[0] * 2 + colors[3]) / 3;
            colors[2] = (colors[3] * 2 + colors[0]) / 3;
            return colors;
        }

        private static int[] blockColor(Etc1sEndpoint endpoint, int selector) {
            int[] modifiers = ETC1_INTENSITY_TABLES[endpoint.getIntensity5()];
            return new int[] {
                clamp255(expand5(endpoint.getRed5()) + modifiers[selector]),
                clamp255(expand5(endpoint.getGreen5()) + modifiers[selector]),
                clamp255(expand5(endpoint.getBlue5()) + modifiers[selector])
            };
        }

        private static void writeBc1Endpoints(byte[] blocks, int blockOffset, int color0, int color1) {
            blocks[blockOffset] = (byte) color0;
            blocks[blockOffset + 1] = (byte) (color0 >>> 8);
            blocks[blockOffset + 2] = (byte) color1;
            blocks[blockOffset + 3] = (byte) (color1 >>> 8);
        }

        private static void fillSelectors(byte[] blocks, int blockOffset, int selectorByte) {
            blocks[blockOffset + 4] = (byte) selectorByte;
            blocks[blockOffset + 5] = (byte) selectorByte;
            blocks[blockOffset + 6] = (byte) selectorByte;
            blocks[blockOffset + 7] = (byte) selectorByte;
        }

        private static int packUnscaledColor(int red, int green, int blue) {
            return blue | (green << 5) | (red << 11);
        }

        private static int packSolution(int lo, int hi, int error) {
            return lo | (hi << 8) | (error << 16);
        }

        private static int solutionLo(int solution) {
            return solution & 0xFF;
        }

        private static int solutionHi(int solution) {
            return (solution >>> 8) & 0xFF;
        }

        private static int solutionError(int solution) {
            return solution >>> 16;
        }

        private static int matchHi(byte[] table, int color) {
            return Byte.toUnsignedInt(table[color * 2]);
        }

        private static int matchLo(byte[] table, int color) {
            return Byte.toUnsignedInt(table[color * 2 + 1]);
        }

        private static int expand6(int value) {
            return (value << 2) | (value >>> 4);
        }
    }
}
